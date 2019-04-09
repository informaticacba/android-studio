/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.art;

import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.TraceParser;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Receives a binary trace file generated by using ART and parse it into a {@link CpuCapture}
 */
public class ArtTraceParser implements TraceParser {

  private final ArtTraceHandler myTraceHandler = new ArtTraceHandler();

  @Override
  public CpuCapture parse(File trace, long traceId) throws IOException {
    VmTraceParser parser = new VmTraceParser(trace, myTraceHandler);
    parser.parse();
    return new CpuCapture(this, traceId, CpuProfiler.CpuProfilerType.ART);
  }

  @Override
  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myTraceHandler.getThreadsGraph();
  }

  @Override
  public Range getRange() {
    return new Range(myTraceHandler.getStartTimeUs(), myTraceHandler.getStartTimeUs() + myTraceHandler.getElapsedTimeUs());
  }

  @Override
  public boolean supportsDualClock() {
    return true;
  }
}
