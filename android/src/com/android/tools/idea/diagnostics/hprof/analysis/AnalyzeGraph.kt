/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.diagnostics.hprof.analysis

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.histogram.Histogram
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.sectionHeader
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toPaddedShortStringAsCount
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toPaddedShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsCount
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.IntList
import com.android.tools.idea.diagnostics.hprof.util.ListProvider
import com.android.tools.idea.diagnostics.hprof.util.PartialProgressIndicator
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import com.android.tools.idea.diagnostics.hprof.visitors.HistogramVisitor
import com.google.common.base.Stopwatch
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TIntIntHashMap
import gnu.trove.TLongArrayList
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

class AnalyzeGraph(private val analysisContext: AnalysisContext, private val listProvider: ListProvider) {

  private val unreachableDisposableObjects = TIntArrayList()
  private var strongRefHistogram: Histogram? = null
  private var softWeakRefHistogram: Histogram? = null
  private var traverseReport: String? = null
  private var dominatorFlameGraph: String? = null

  private val parentList = analysisContext.parentList

  private fun setParentForObjectId(objectId: Long, parentId: Long) {
    parentList[objectId.toInt()] = parentId.toInt()
  }

  private fun getParentIdForObjectId(objectId: Long): Long {
    return parentList[objectId.toInt()].toLong()
  }

  private val nominatedInstances = HashMap<ClassDefinition, TIntHashSet>()

  fun analyze(progress: ProgressIndicator): AnalysisReport = AnalysisReport().apply {
    val includePerClassSection = analysisContext.config.perClassOptions.classNames.isNotEmpty()

    val traverseProgress =
      if (includePerClassSection) PartialProgressIndicator(progress, 0.0, 0.5) else progress

    val analyzeDisposer = AnalyzeDisposer(analysisContext)
    analyzeDisposer.prepareDisposerChildren()

    traverseInstanceGraph(traverseProgress, this)

    analyzeDisposer.computeDisposedObjectsIDs()

    // Histogram section
    val histogramOptions = analysisContext.config.histogramOptions
    if (histogramOptions.includeByCount || histogramOptions.includeBySize) {
      mainReport.appendln(sectionHeader("Histogram"))
      mainReport.append(prepareHistogramSection())
    }

    if (histogramOptions.includeSummary) {
      mainReport.appendln(sectionHeader("Heap summary"))
      mainReport.append(traverseReport)
    }

    // Per-class section
    if (includePerClassSection) {
      val perClassProgress = PartialProgressIndicator(progress, 0.5, 0.5)
      mainReport.appendln(sectionHeader("Instances of each nominated class"))
      mainReport.append(preparePerClassSection(perClassProgress))
    }

    // Disposer sections
    if (config.disposerOptions.includeDisposerTree) {
      mainReport.appendln(sectionHeader("Disposer tree"))
      mainReport.append(analyzeDisposer.prepareDisposerTreeSection())
    }
    if (config.disposerOptions.includeDisposerTreeSummary) {
      mainReport.appendln(sectionHeader("Disposer tree summary"))
      mainReport.append(analyzeDisposer.prepareDisposerTreeSummarySection(config.disposerOptions.disposerTreeSummaryOptions))
    }
    if (config.disposerOptions.includeDisposedObjectsSummary || config.disposerOptions.includeDisposedObjectsDetails) {
      mainReport.appendln(sectionHeader("Disposed objects"))
      mainReport.append(analyzeDisposer.prepareDisposedObjectsSection())
    }

    // Dominator tree flame graph
    if (config.dominatorTreeOptions.includeDominatorTree) {
      mainReport.appendln(sectionHeader("Dominator tree flame graph"))
      mainReport.append(dominatorFlameGraph)
    }

  }

  private fun preparePerClassSection(progress: PartialProgressIndicator): String = buildString {
    val histogram = analysisContext.histogram
    val perClassOptions = analysisContext.config.perClassOptions

    if (perClassOptions.includeClassList) {
      appendln("Nominated classes:")
      perClassOptions.classNames.forEach { name ->
        val (classDefinition, totalInstances, totalBytes) =
          histogram.entries.find { entry -> entry.classDefinition.name == name } ?: return@forEach
        val prettyName = classDefinition.prettyName
        appendln(" --> [${toShortStringAsCount(totalInstances)}/${toShortStringAsSize(totalBytes)}] " + prettyName)
      }
      appendln()
    }

    val nav = analysisContext.navigator
    var counter = 0
    val nominatedClassNames = config.perClassOptions.classNames
    val stopwatch = Stopwatch.createUnstarted()
    nominatedClassNames.forEach { className ->
      val classDefinition = nav.classStore[className]
      val set = nominatedInstances[classDefinition]!!
      progress.fraction = counter.toDouble() / nominatedInstances.size
      progress.text2 = "Processing: ${set.size()} ${classDefinition.prettyName}"
      stopwatch.reset().start()
      appendln("CLASS: ${classDefinition.prettyName} (${set.size()} objects)")
      val referenceRegistry = GCRootPathsTree(analysisContext, perClassOptions.treeDisplayOptions, classDefinition)
      set.forEach { objectId ->
        referenceRegistry.registerObject(objectId)
        true
      }
      set.clear()
      append(referenceRegistry.printTree())
      if (config.metaInfoOptions.include) {
        appendln("Report for ${classDefinition.prettyName} created in $stopwatch")
      }
      appendln()
      counter++
    }
    progress.fraction = 1.0
  }

  private fun prepareHistogramSection(): String = buildString {
    val strongRefHistogram = getAndClearStrongRefHistogram()
    val softWeakRefHistogram = getAndClearSoftWeakHistogram()

    val histogram = analysisContext.histogram
    val histogramOptions = analysisContext.config.histogramOptions

    append(
      Histogram.prepareMergedHistogramReport(histogram, "All",
                                             strongRefHistogram, "Strong-ref", histogramOptions))

    val unreachableObjectsCount = histogram.instanceCount - strongRefHistogram.instanceCount - softWeakRefHistogram.instanceCount
    val unreachableObjectsSize = histogram.bytesCount - strongRefHistogram.bytesCount - softWeakRefHistogram.bytesCount
    appendln("Unreachable objects: ${toPaddedShortStringAsCount(
      unreachableObjectsCount)}  ${toPaddedShortStringAsSize(unreachableObjectsSize)}")
  }

  enum class WalkGraphPhase {
    StrongReferencesNonLocalVariables,
    StrongReferencesLocalVariables,
    DisposerTree,
    SoftReferences,
    WeakReferences,
    CleanerFinalizerReferences,
    Finished
  }

  private val config = analysisContext.config

  private fun traverseInstanceGraph(progress: ProgressIndicator, report: AnalysisReport)
  {
    val traverseOptions = config.traverseOptions
    val onlyStrongReferences = traverseOptions.onlyStrongReferences
    val includeDisposerRelationships = traverseOptions.includeDisposerRelationships
    val includeFieldInformation = traverseOptions.includeFieldInformation

    val nav = analysisContext.navigator
    val classStore = analysisContext.classStore
    val sizesList = analysisContext.sizesList
    val visitedList = analysisContext.visitedList
    val refIndexList = analysisContext.refIndexList

    val roots = nav.createRootsIterator()
    nominatedInstances.clear()

    val nominatedClassNames = config.perClassOptions.classNames
    nominatedClassNames.forEach {
      nominatedInstances[classStore[it]] = TIntHashSet()
    }

    progress.text2 = "Collect all object roots"

    var toVisit = TIntArrayList()
    var toVisit2 = TIntArrayList()

    val rootsSet = TIntHashSet()
    val frameRootsSet = TIntHashSet()

    // Mark all roots to be visited, set them as their own parents
    while (roots.hasNext()) {
      val rootObject = roots.next()
      val rootObjectId = rootObject.id.toInt()
      if (rootObject.reason.javaFrame) {
        frameRootsSet.add(rootObjectId)
      }
      else {
        addIdToSetIfOrphan(rootsSet, rootObjectId)
      }
    }

    // Mark all class object as to be visited, set them as their own parents
    classStore.forEachClass { classDefinition ->
      addIdToSetIfOrphan(rootsSet, classDefinition.id.toInt())
      classDefinition.staticFields.forEach { staticField ->
        addIdToSetIfOrphan(rootsSet, staticField.objectId.toInt())
      }
      classDefinition.constantFields.forEach { objectId ->
        addIdToSetIfOrphan(rootsSet, objectId.toInt())
      }
    }

    toVisit.add(rootsSet.toArray())

    var leafCounter = 0

    progress.text2 = "Traversing instance graph"

    val strongRefHistogramEntries = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()
    val reachableNonStrongHistogramEntries = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()
    val softReferenceIdToParentMap = TIntIntHashMap()
    val weakReferenceIdToParentMap = TIntIntHashMap()

    var visitedInstancesCount = 0
    val stopwatch = Stopwatch.createStarted()
    val references = TLongArrayList()

    var visitedCount = 0
    var strongRefVisitedCount = 0
    var softWeakVisitedCount = 0
    var edgeCount = 0

    var finalizableBytes = 0L
    var softBytes = 0L
    var weakBytes = 0L

    var phase = WalkGraphPhase.StrongReferencesNonLocalVariables // initial state

    val cleanerObjects = TIntArrayList()
    val sunMiscCleanerClass = classStore.getClassIfExists("sun.misc.Cleaner")
    val finalizerClass = classStore.getClassIfExists("java.lang.ref.Finalizer")

    while (!toVisit.isEmpty) {
      for (i in 0 until toVisit.size()) {
        val id = toVisit[i]

        // Disposer.ourTree is only visited during DisposerTree phase to give opportunity for
        if (includeDisposerRelationships &&
            id == analysisContext.diposerTreeObjectId &&
            phase < WalkGraphPhase.DisposerTree) {
          continue
        }

        nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
        val currentObjectClass = nav.getClass()

        if ((currentObjectClass == sunMiscCleanerClass || currentObjectClass == finalizerClass)
            && phase < WalkGraphPhase.CleanerFinalizerReferences) {
          if (!onlyStrongReferences) {
            // Postpone visiting sun.misc.Cleaner and java.lang.ref.Finalizer objects until later phase
            cleanerObjects.add(id)
          }
          continue
        }

        visitedInstancesCount++
        nominatedInstances[currentObjectClass]?.add(id)

        var isLeaf = true
        nav.copyReferencesTo(references)
        val currentObjectIsArray = currentObjectClass.isArray()

        // Postpone any soft references encountered before the phase that handles them
        if (phase < WalkGraphPhase.SoftReferences && nav.getSoftReferenceId() != 0L) {
          if (!onlyStrongReferences) {
            softReferenceIdToParentMap.put(nav.getSoftReferenceId().toInt(), id)
          }
          references[nav.getSoftWeakReferenceIndex()] = 0L
        }

        // Postpone any weak references encountered before the phase that handles them
        if (phase < WalkGraphPhase.WeakReferences && nav.getWeakReferenceId() != 0L) {
          if (!onlyStrongReferences) {
            weakReferenceIdToParentMap.put(nav.getWeakReferenceId().toInt(), id)
          }
          references[nav.getSoftWeakReferenceIndex()] = 0L
        }

        val size = nav.getObjectSize()
        val nonDisposerReferences = references.size()

        // Inline children from the disposer tree
        if (includeDisposerRelationships && analysisContext.disposerParentToChildren.contains(id)) {
          if (phase >= WalkGraphPhase.DisposerTree) {
            unreachableDisposableObjects.add(id)
          }
          analysisContext.disposerParentToChildren[id].forEach {
            references.add(it.toLong())
            true
          }
        }

        for (j in 0 until references.size()) {
          val referenceId = references[j].toInt()
          if (referenceId != 0) edgeCount++
          if (addIdToListAndSetParentIfOrphan(toVisit2, referenceId, id)) {
            if (includeFieldInformation) {
              refIndexList[referenceId] = when {
                currentObjectIsArray -> RefIndexUtil.ARRAY_ELEMENT
                j >= nonDisposerReferences -> RefIndexUtil.DISPOSER_CHILD
                j < RefIndexUtil.MAX_FIELD_INDEX -> j + 1
                else -> RefIndexUtil.FIELD_OMITTED // Too many reference fields
              }
            }
            isLeaf = false
          }
        }

        // Ordered list of visited nodes. Parent is always before its children.
        visitedList[visitedCount++] = id

        // Store size of the object. Later pass will update the size by adding sizes of children
        // Size in DWORDs to support graph sizes up to 10GB.
        var sizeDivBy4 = (size + 3) / 4
        if (sizeDivBy4 == 0) sizeDivBy4 = 1
        sizesList[id] = sizeDivBy4

        // Update histogram (separately for Strong-references and other reachable objects)
        var histogramEntries: HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>
        if (phase == WalkGraphPhase.StrongReferencesNonLocalVariables || phase == WalkGraphPhase.StrongReferencesLocalVariables ||
          phase == WalkGraphPhase.DisposerTree) {
          histogramEntries = strongRefHistogramEntries
          if (isLeaf) {
            leafCounter++
          }
          strongRefVisitedCount++
        }
        else {
          histogramEntries = reachableNonStrongHistogramEntries
          when (phase) {
            WalkGraphPhase.CleanerFinalizerReferences -> finalizableBytes += size
            WalkGraphPhase.SoftReferences -> softBytes += size
            else -> {
              assert(phase == WalkGraphPhase.WeakReferences)
              weakBytes += size
            }
          }
          softWeakVisitedCount++
        }
        histogramEntries.getOrPut(currentObjectClass) {
          HistogramVisitor.InternalHistogramEntry(currentObjectClass)
        }.addInstance(size.toLong())
      }

      // Update process
      progress.fraction = (1.0 * visitedInstancesCount / nav.instanceCount)

      // Prepare next level of objects for processing
      toVisit.resetQuick()
      val tmp = toVisit
      toVisit = toVisit2
      toVisit2 = tmp

      // If no more object to visit at this phase, transition to the next
      while (toVisit.size() == 0 && phase != WalkGraphPhase.Finished) {
        // Next state
        phase = WalkGraphPhase.values()[phase.ordinal + 1]

        when (phase) {
          WalkGraphPhase.StrongReferencesLocalVariables ->
            frameRootsSet.forEach { id ->
              addIdToListAndSetParentIfOrphan(toVisit, id, id)
              true
            }
          WalkGraphPhase.CleanerFinalizerReferences -> {
            toVisit.add(cleanerObjects.toNativeArray())
            cleanerObjects.clear()
          }
          WalkGraphPhase.SoftReferences -> {
            softReferenceIdToParentMap.forEachEntry { softId, parentId ->
              if (addIdToListAndSetParentIfOrphan(toVisit, softId, parentId)) {
                refIndexList[softId] = RefIndexUtil.SOFT_REFERENCE
              }

              true
            }
            // No need to store the list anymore
            softReferenceIdToParentMap.clear()
            softReferenceIdToParentMap.compact()
          }
          WalkGraphPhase.WeakReferences -> {
            weakReferenceIdToParentMap.forEachEntry { weakId, parentId ->
              if (addIdToListAndSetParentIfOrphan(toVisit, weakId, parentId)) {
                refIndexList[weakId] = RefIndexUtil.WEAK_REFERENCE
              }
              true
            }
            // No need to store the list anymore
            weakReferenceIdToParentMap.clear()
            weakReferenceIdToParentMap.compact()
          }
          WalkGraphPhase.DisposerTree -> {
            if (analysisContext.diposerTreeObjectId != 0) {
              toVisit.add(analysisContext.diposerTreeObjectId)
            }
          }
          else -> Unit // No work for other state transitions
        }
      }
    }

    if (config.dominatorTreeOptions.includeDominatorTree) {
      val usableDiskSpace = File(FileUtil.getTempDirectory()).usableSpace
      if (usableDiskSpace - estimateDominatorTempFilesSize(visitedCount, edgeCount) > config.dominatorTreeOptions.diskSpaceThreshold) {
        try {
          rootsSet.addAll(frameRootsSet.toArray())
          computeDominatorFlameGraph(nav, rootsSet, sizesList, edgeCount, report)
        }
        catch (e: Exception) {
          val baos = ByteArrayOutputStream()
          val pw = PrintWriter(baos)
          e.printStackTrace(pw)
          pw.flush()
          dominatorFlameGraph = baos.toString()
        }
      } else {
        dominatorFlameGraph = "Omitted due to low disk space"
      }
    }
    rootsSet.clear()
    rootsSet.compact()

    // Assert that any postponed objects have been handled
    assert(cleanerObjects.isEmpty)
    assert(softReferenceIdToParentMap.isEmpty)
    assert(weakReferenceIdToParentMap.isEmpty)

    // Histograms are accessible publicly after traversal is complete
    strongRefHistogram = Histogram(
      strongRefHistogramEntries
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      strongRefVisitedCount.toLong())

    softWeakRefHistogram = Histogram(
      reachableNonStrongHistogramEntries
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      softWeakVisitedCount.toLong())

    // Update size field in non-leaves to reflect the size of the whole subtree. Right after traversal
    // the size field is initialized to the size of the given object.
    // Traverses objects in the reverse order, so a child is always visited before its parent.
    // This assures size field is correctly set for a given before its added to the size field of the parent.
    val stopwatchUpdateSizes = Stopwatch.createStarted()
    var index = visitedCount - 1
    while (index >= 0) {
      val id = visitedList[index]
      val parentId = parentList[id]
      if (id != parentId) {
        sizesList[parentId] += sizesList[id]
      }
      index--
    }
    stopwatchUpdateSizes.stop()

    traverseReport = buildString {
      if (config.metaInfoOptions.include) {
        appendln("Analysis completed! Visited instances: $visitedInstancesCount, time: $stopwatch")
        appendln("Update sizes time: $stopwatchUpdateSizes")
        appendln("Leaves found: $leafCounter")
      }

      appendln("Class count: ${classStore.size()}")

      // Adds summary of object count by reachability
      appendln("Finalizable size: ${toShortStringAsSize(finalizableBytes)}")
      appendln("Soft-reachable size: ${toShortStringAsSize(softBytes)}")
      appendln("Weak-reachable size: ${toShortStringAsSize(weakBytes)}")
      appendln("Reachable only from disposer tree: ${unreachableDisposableObjects.size()}")
      TruncatingPrintBuffer(10, 0, this::appendln).use { buffer ->
        val unreachableChildren = TIntHashSet()
        unreachableDisposableObjects.forEach { id ->
          analysisContext.disposerParentToChildren[id]?.let { unreachableChildren.addAll(it.toNativeArray()) }
          true
        }
        unreachableDisposableObjects.forEach { id ->
          if (unreachableChildren.contains(id)) {
            return@forEach true
          }
          buffer.println(" * ${nav.getClassForObjectId(id.toLong()).name} (${toShortStringAsSize(sizesList[id].toLong() * 4)})")
          true
        }
      }
    }
  }

  private fun estimateDominatorTempFilesSize(objectCount: Int, edgeCount: Int): Long {
    return 20L * objectCount + 10L * edgeCount
  }

  private fun computeDominatorFlameGraph(nav: ObjectNavigator, rootsSet: TIntHashSet, sizesList: IntList, edgeCount: Int, report: AnalysisReport) {
    val totalStopwatch = Stopwatch.createUnstarted()
    val postorderStopwatch = Stopwatch.createUnstarted()
    val incomingEdgesStopwatch = Stopwatch.createUnstarted()
    val sortIncomingEdgesStopwatch = Stopwatch.createUnstarted()
    val dominatorsStopwatch = Stopwatch.createUnstarted()
    val retainedSizesStopwatch = Stopwatch.createUnstarted()
    val flameGraphStopwatch = Stopwatch.createUnstarted()

    totalStopwatch.start()
    postorderStopwatch.start()
    val objectCount = nav.instanceCount.toInt()
    // this is slightly oversized; it only needs to be large enough to contain all strongly-referenced objects
    val postorderList = listProvider.createIntList("postorderList", (objectCount + 2).toLong())
    val postorderNumbers = listProvider.createIntList("postorderNumbers", (objectCount + 2).toLong())

    // compute postorder numbers
    val nodeStack = listProvider.createIntList("dominatorBuf1", (objectCount+2).toLong())
    val childrenStack = listProvider.createIntList("childStack", (edgeCount + rootsSet.size()).toLong())
    val childrenStackOffsets = listProvider.createIntList("dominatorBuf2", (objectCount + 2).toLong())
    val childrenStackSizes = listProvider.createIntList("dominatorBuf3", (objectCount + 2).toLong())
    var csEntries = 0
    var poEdgeCount = rootsSet.size()
    for (id in rootsSet) {
      childrenStack[csEntries++] = id
    }

    childrenStackSizes[0] = csEntries
    childrenStackOffsets[0] = 0
    childrenStackOffsets[1] = csEntries

    var nsSize = 0
    var csSize = 1
    var maxPonum = 1
    var maxStackDepth = 0
    val refList = TLongArrayList()
    while (csSize != 0) {
      if (childrenStackSizes[csSize-1] > 0) {
        val child = childrenStack[childrenStackOffsets[csSize-1] + childrenStackSizes[csSize-1] - 1]
        childrenStackSizes[csSize-1]--
        if (postorderNumbers[child] == 0) {
          postorderNumbers[child] = -1
          nodeStack[nsSize++] = child
          nav.goTo(child.toLong())
          nav.copyReferencesTo(refList)
          var refsAdded = 0
          for (i in 0 until refList.size()) {
            if (refList[i] != 0L) {
              childrenStack[csEntries++] = refList[i].toInt()
              refsAdded++
              poEdgeCount++
            }
          }
          childrenStackSizes[csSize] = refsAdded
          childrenStackOffsets[csSize+1] = childrenStackOffsets[csSize] + refsAdded
          csSize++
          maxStackDepth = max(csSize, maxStackDepth)
        }
      } else {
        csSize--
        if (csSize == 0) break
        csEntries = childrenStackOffsets[csSize]
        if (nsSize != 0) {
          val n = nodeStack[nsSize - 1]
          postorderList[maxPonum] = n
          postorderNumbers[n] = maxPonum++
          nsSize--
        }
      }
    }
    val rootPonum = maxPonum
    postorderNumbers[objectCount+1] = rootPonum
    postorderStopwatch.stop()

    incomingEdgesStopwatch.start()
    /* To accelerate the iterative dominance calculation, we can take advantage of the fact that an object's idom can only change
     * if at least one of its successors idoms have changed since the last time it was computed. After the first couple iterations,
     * most idoms stay the same, so much time is wasted on recomputing idoms that are guaranteed not to change.
     *
     * Drawing inspiration from garbage collector card tables, we keep a bitmap that records which objects need to be updated. Each
     * bit corresponds to a range of postorder numbers (probably 128 or 256), to keep the card table small and rapidly scannable. If
     * that bit is set, every object in that range will have its idom recomputed. This is roughly 5x faster than the simple approach.
     */

    // cards represent as small of a power-of-2 sized range as possible while ensuring that card indices can fit in a short
    val cardBits = if (maxPonum < 65536) 0 else (Math.log(maxPonum / 65536.0) / Math.log(2.0) + 1).toInt()
    val cardSize = 1 shl cardBits
    val ncards = (maxPonum + cardSize - 1) / cardSize
    val ncardChunks = (ncards + 63) / 64
    val dirtyCards = LongArray(ncardChunks)
    dirtyCards.fill(-1) // initially everything needs to have its idom computed
    // outgoingCardRefs contains, for each object p, a list of which cards must be marked dirty when p's idom changes, i.e., the cards
    // containing the successors of p. These lists can contain duplicates if some of the successors are in the same card, but removing
    // them is significantly more costly than just performing the redundant card updates.
    val outgoingCardRefs = listProvider.createUShortList("cardRefs", poEdgeCount.toLong())
    // outgoingCardListOffsets[p] is the index in outgoingCardRefs where the list for object 'p' begins
    val outgoingCardListOffsets = nodeStack
    outgoingCardListOffsets.clear(maxPonum+1)

    val edgeListOffsets = childrenStackOffsets
    edgeListOffsets.clear(maxPonum+1)

    // first count the number of incoming edges for each object and fill the card lists
    val references = TLongArrayList()
    var ncardrefs = 0
    for (i in 1 until maxPonum) {
      val id = postorderList[i]
      nav.goTo(id.toLong())
      nav.copyReferencesTo(references)
      if (rootsSet.contains(id)) {
        edgeListOffsets[i]++
      }
      outgoingCardListOffsets[i] = ncardrefs
      for (j in 0 until references.size()) {
        if (references[j] != 0L) {
          val target = postorderNumbers[references[j].toInt()]
          edgeListOffsets[target]++
          outgoingCardRefs[ncardrefs++] = target shr cardBits
        }
      }
    }
    outgoingCardListOffsets[maxPonum] = ncardrefs
    // compute the partial sums of the incoming edge counts, in place
    var next: Int
    var psum = edgeListOffsets[0]
    for (i in 0 until maxPonum) {
      next = edgeListOffsets[i + 1]
      edgeListOffsets[i + 1] = psum
      psum += next
    }
    edgeListOffsets[0] = 0

    // concatenated incoming edge lists for each object, in postorder traversal order;
    // edgeListOffsets[p] gives the starting index for the list for the object with postorder number p
    val incomingEdges: IntList
    if (poEdgeCount < edgeCount + rootsSet.size()) {
      // if childrenStack is big enough, reuse it
      incomingEdges = childrenStack
      incomingEdges.clear(poEdgeCount)
    } else {
      incomingEdges = listProvider.createIntList("incoming", poEdgeCount.toLong())
    }
    // stores the number of edges added to each incoming edge list so far
    val edgeListIndices = childrenStackSizes
    edgeListIndices.clear(maxPonum+1)

    fun getEdge(ponum: Int, index: Int) = incomingEdges[edgeListOffsets[ponum] + index]

    fun addEdge(ponum: Int, parent: Int) {
      val nextFreeSlot = edgeListOffsets[ponum] + edgeListIndices[ponum]
      incomingEdges[nextFreeSlot] = parent
      edgeListIndices[ponum]++
    }

    fun numIncomingEdges(ponum: Int) = edgeListOffsets[ponum + 1] - edgeListOffsets[ponum]

    // now that the incoming edges have been counted and the offsets determined, fill in the lists
    for (i in 1 until maxPonum) {
      val id = postorderList[i]
      nav.goTo(id.toLong())
      nav.copyReferencesTo(references)
      if (rootsSet.contains(id)) {
        addEdge(i, rootPonum)
      }
      for (j in 0 until references.size()) {
        if (references[j] != 0L) {
          val target = postorderNumbers[references[j].toInt()]
          addEdge(target, i)
        }
      }
    }
    incomingEdgesStopwatch.stop()
    sortIncomingEdgesStopwatch.start()
    for (i in 0 until maxPonum-1) {
      val size = edgeListOffsets[i+1] - edgeListOffsets[i]
      if (size <= 1) continue
      val arr = IntArray(size)
      for (j in 0 until size) {
        arr[j] = incomingEdges[edgeListOffsets[i] + j]
      }
      Arrays.sort(arr)
      for (j in 0 until size) {
        incomingEdges[edgeListOffsets[i] + j] = arr[j]
      }
    }
    sortIncomingEdgesStopwatch.stop()

    /* Compute DOM sets by iteratively applying this recurrence until a fixed point is reached:
     *   DOM(n) = intersection(DOM(p_1), DOM(p_2), ..., DOM(p_k))
     * where p_i is the i'th predecessor of n, and initially DOM(ROOT) = {ROOT}, and for n != ROOT, DOM(n) is the entire node set.
     * DOM(n) can be represented more compactly by only storing the immediate dominator for each node, which forms a tree with idoms
     * as the parent pointers. The DOM set for a node n consists of all of the nodes in the path from n up to the root.
     * This iteration is most efficient when done in reverse-postorder (if the graph is reducible in the sense of flow-graph reducibility,
     * it is guaranteed to terminate in 2 iterations. Unfortunately heaps tend not to be reducible, and while it can take hundreds of
     * iterations to quiesce completely, very few idoms are changed after the first couple dozen)
     */
    dominatorsStopwatch.start()
    val idomList = edgeListIndices // edgeListIndices is no longer needed, so we can reuse this buffer for the idoms and save some space
    idomList.clear(maxPonum+1)
    idomList[rootPonum] = rootPonum

    /* Find the intersection of the DOM sets for nodes a and b. This will be the common prefix of the paths from the root to a and b,
     * so will be represented by the postorder number for the nearest common ancestor of a and b. Since idom(n) > n, the NCA can be
     * found efficiently by walking up the tree from a and b, at each step moving only the one with the smaller postorder number,
     * until either the two are equal or the root is reached.
     */
    var intersectCalls = 0
    fun intersect(a: Int, b: Int): Int {
      intersectCalls++
      var i = a
      var j = b
      while (i != j) {
        while (i < j) {
          if (i == 0) return j
          i = idomList[i]
        }
        while (j < i) {
          if (j == 0) return i
          j = idomList[j]
        }
      }
      return i
    }

    // compute the intersection of the idoms of the predecessors of object i; return true if the idom for i changed.
    var rootSkipIntersectCalls = 0
    fun updateIdom(i: Int): Boolean {
      val numIncoming = numIncomingEdges(i)
      var newIdom = getEdge(i, numIncoming - 1)
      var j = numIncoming - 2
      while (j >= 0) {
        val parent = getEdge(i, j)
        if (parent == 0) break
        val intersection = intersect(newIdom, parent)
        if (intersection != 0) {
          newIdom = intersection
        }
        if (newIdom == rootPonum) {
          rootSkipIntersectCalls += j
          break
        }
        j--
      }
      if (newIdom != idomList[i]) {
        idomList[i] = newIdom
        return true
      }
      return false
    }

    fun markCard(cardNumber: Int, value: Boolean) {
      val chunk = cardNumber shr 6
      val card = cardNumber and 0x3F
      if (value) {
        dirtyCards[chunk] = dirtyCards[chunk] or (1L shl card)
      } else {
        dirtyCards[chunk] = dirtyCards[chunk] and (1L shl card).inv()
      }
    }

    // scan through the card table in reverse order, recomputing idoms for any dirty cards. Do this repeatedly until either a fixed point
    // or the iteration cap is reached
    var nchanged = 1
    var iter = 0
    var idomUpdates = 0
    var prevIntersectCalls = 0
    var updateCallsSavedByCardTable = 0L
    while (nchanged != 0 && iter < config.dominatorTreeOptions.maxDominatorIterations) {
      val iterationStopwatch = Stopwatch.createStarted()
      var nupdates = 0
      nchanged = 0
      var chunkIndex = ncardChunks - 1
      while (chunkIndex >= 0) {
        val chunk = dirtyCards[chunkIndex]
        if (chunk == 0L) { // quickly skip chunks with no set bits - this allows skipping thousands of objects at a time
          updateCallsSavedByCardTable += cardSize * 64
          chunkIndex--
          continue
        }
        var card = 63
        while (card >= 0) {
          if ((chunk and (1L shl card)) != 0L) {
            val basePonum = cardSize * (chunkIndex * 64 + card)
            var i = cardSize - 1
            markCard(chunkIndex * 64 + card, false) // we are about to recompute everything in this card. mark it clean
            while (i >= 0) {
              val ponum = basePonum + i
              if (ponum == 0) break
              if (ponum >= rootPonum) {
                i--
                continue
              }
              if (idomList[ponum] == rootPonum) { // if the idom is already the root, it can't change any more - no need to recompute
                i--
                continue
              }

              idomUpdates++
              nupdates++
              if (updateIdom(ponum)) { // idom changed - need to mark successor cards dirty
                var chidx = outgoingCardListOffsets[ponum]
                while (chidx < outgoingCardListOffsets[ponum+1]) {
                  val targetCard = outgoingCardRefs[chidx]
                  markCard(targetCard, true)
                  chidx++
                }
                nchanged++
              }
              i--
            }
          } else {
            updateCallsSavedByCardTable += cardSize
          }
          card--
        }
        chunkIndex--
      }
      LOG.debug("iteration $iter recomputed $nupdates and changed $nchanged idoms in $iterationStopwatch; called intersect() ${intersectCalls - prevIntersectCalls} times")
      prevIntersectCalls = intersectCalls
      iter++
    }
    dominatorsStopwatch.stop()

    retainedSizesStopwatch.start()
    val retainedSizes = outgoingCardListOffsets // reuse this buffer, it is no longer needed
    for (i in 1 until maxPonum) {
      retainedSizes[i] = sizesList[postorderList[i]]
    }
    for (i in 1 until maxPonum) {
      retainedSizes[idomList[i]] += retainedSizes[i]
    }
    retainedSizesStopwatch.stop()

    flameGraphStopwatch.start()
    val idomTreeChildren = mutableMapOf<Int, MutableList<Int>>()
    for (i in 1 until maxPonum) {
      if (retainedSizes[i] * 4L >= config.dominatorTreeOptions.minNodeSize) {
        idomTreeChildren.computeIfAbsent(idomList[i]) { mutableListOf() }.add(i)
      }
    }

    fun signatureFor(ponum: Int): String {
      return if (ponum == rootPonum) "" else nav.getClassForObjectId(postorderList[ponum].toLong()).name.dropLastWhile { it == ';' }
    }

    /* Produce a flame graph of the idom tree in async-profiler format. The tree is truncated where the retained size
     * of a node falls below minNodeSize, or if the depth gets above maxDepth (very long chains can be created with
     * linked lists, for example).
     */
    var renderedNodes = 0
    fun dumpFlameGraph(poNumber: Int, tpb: TruncatingPrintBuffer, pathPrefix: String = "", depth: Int = 0) {
      val signature = (if (pathPrefix == "") "" else "$pathPrefix;") + signatureFor(poNumber)
      var renderedChildrenSize = 0

      if (depth < config.dominatorTreeOptions.maxDepth) {
        idomTreeChildren[poNumber]?.sortedByDescending { p -> retainedSizes[p] }?.forEach { p ->
          dumpFlameGraph(p, tpb, signature, depth + 1)
          renderedChildrenSize += retainedSizes[p]
        }
      }
      if (poNumber != rootPonum) {
        tpb.println("$signature ${(retainedSizes[poNumber] - renderedChildrenSize).toLong() * 4}")
        renderedNodes++
      }
    }

    dominatorFlameGraph = buildString {
      val tpb = TruncatingPrintBuffer(config.dominatorTreeOptions.headLimit, 0, this::appendln)
      dumpFlameGraph(rootPonum, tpb)
    }
    flameGraphStopwatch.stop()
    totalStopwatch.stop()

    fun Double.round(): String {
      val s = toString()
      if (!s.contains('.')) return s
      return s.substring(0, min(s.indexOf(".")+3, s.length))
    }

    report.metaInfo.apply {
      appendln("Dominator phase total time: $totalStopwatch")
      appendln("  Compute postorder numbers: $postorderStopwatch")
      appendln("    edgeCount = $edgeCount, poEdgeCount = $poEdgeCount, rootsSet size = ${rootsSet.size()}, maxPonum = $maxPonum; max stack depth = $maxStackDepth")
      appendln("  Compute incoming edges + card refs: $incomingEdgesStopwatch")
      appendln("  Sort incoming edges: $sortIncomingEdgesStopwatch")
      appendln("  Dominator computation: $dominatorsStopwatch")
      appendln("    $iter iterations")
      appendln(
        "    $idomUpdates idom updates (${(idomUpdates.toDouble() / maxPonum).round()}x per node); $intersectCalls calls to intersect (${(intersectCalls.toDouble() / idomUpdates).round()} per idomUpdate)")
      appendln("    card table is $ncards bits x $cardSize obj/bit, saved $updateCallsSavedByCardTable idom updates")
      appendln("    aborting updateIdom when root is hit saved $rootSkipIntersectCalls intersect calls")
      appendln("  Compute retained sizes: $retainedSizesStopwatch")
      appendln("  Emit flame graph: $flameGraphStopwatch")
      appendln("    retained size cutoff: ${config.dominatorTreeOptions.minNodeSize}")
      appendln("    depth cutoff: ${config.dominatorTreeOptions.maxDepth}")
      appendln(
        "    pruned tree contains $renderedNodes nodes ${if (renderedNodes > config.dominatorTreeOptions.headLimit) "(truncated to ${config.dominatorTreeOptions.headLimit})" else ""}")
    }
  }

  private fun IntList.clear(size: Int) {
    for (i in 0 until size) {
      this[i] = 0
    }
  }

  /**
   * Adds object id to the list, only if the object does not have a parent object. Object will
   * also have a parent assigned.
   * For root objects, parentId should point back to the object making the object its own parent.
   * For other cases parentId can be provided.
   *
   * @return true if object was added to the list.
   */
  private fun addIdToListAndSetParentIfOrphan(list: TIntArrayList, id: Int, parentId: Int = id): Boolean {
    if (id != 0 && getParentIdForObjectId(id.toLong()) == 0L) {
      setParentForObjectId(id.toLong(), parentId.toLong())
      list.add(id)
      return true
    }
    return false
  }

  /**
   * Adds object id to the set, only if the object does not have a parent object and is not yet in the set.
   * Object will also have a parent assigned.
   * For root objects, parentId should point back to the object making the object its own parent.
   * For other cases parentId can be provided.
   *
   * @return true if object was added to the set.
   */
  private fun addIdToSetIfOrphan(set: TIntHashSet, id: Int, parentId: Int = id): Boolean {
    if (id != 0 && getParentIdForObjectId(id.toLong()) == 0L && set.add(id)) {
      setParentForObjectId(id.toLong(), parentId.toLong())
      return true
    }
    return false
  }

  private fun getAndClearStrongRefHistogram(): Histogram {
    val result = strongRefHistogram
    strongRefHistogram = null
    return result ?: throw IllegalStateException("Graph not analyzed.")
  }

  private fun getAndClearSoftWeakHistogram(): Histogram {
    val result = softWeakRefHistogram
    softWeakRefHistogram = null
    return result ?: throw IllegalStateException("Graph not analyzed.")
  }

  companion object {
    private val LOG = Logger.getInstance(AnalyzeGraph::class.java)
  }
}
