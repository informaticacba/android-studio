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
package com.android.tools.idea.diagnostics;

import static org.hamcrest.CoreMatchers.hasItems;

import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class StudioReportDatabaseTest {
  @Rule
  public TemporaryFolder myTestFolder = new TemporaryFolder();

  private File databaseFile;
  private StudioReportDatabase db;

  @Before
  public void setup() {
    databaseFile = new File(myTestFolder.getRoot(), "threads.dmp");
    db = new StudioReportDatabase(databaseFile);
  }

  @Test
  public void testParser() throws IOException {
    Path t1 = createTempFileWithThreadDump("1");
    Path t2 = createTempFileWithThreadDump("1");

    db.appendPerformanceThreadDump(t1, "test");
    db.appendPerformanceThreadDump(t2, "test");

    List<StudioReportDetails> reports = db.reapReportDetails();
    List<Path> paths = reports.stream().map(r -> r.getThreadDumpPath()).collect(Collectors.toList());
    assertThat(paths, hasItems(t1, t2));

    reports = db.reapReportDetails();
    assertTrue(reports.isEmpty());
  }

  @Test
  public void testDifferentTypes() throws IOException {
    Path h1 = createTempFileWithThreadDump("H1");
    Path t1 = createTempFileWithThreadDump("T1");

    Path t2 = createTempFileWithThreadDump("T2");

    Path h3 = createTempFileWithThreadDump("H3");
    Path t3 = createTempFileWithThreadDump("T3");

    db.appendHistogram(t1, h1, "test");
    db.appendPerformanceThreadDump(t2, "test");
    db.appendHistogram(t3, h3, "test");


    List<StudioReportDetails> reports = db.reapReportDetails();

    assertEquals(3, reports.size());
    assertEquals(2, reports.stream().filter(r -> r.getType().equals("Histogram")).count());
    assertEquals(1, reports.stream().filter(r -> r.getType().equals("PerformanceThreadDump")).count());
  }

  @Test
  public void testHistogramContent() throws IOException {
    Path h1 = createTempFileWithThreadDump("H1");
    Path t1 = createTempFileWithThreadDump("T1");

    db.appendHistogram(t1, h1, "Histogram description");

    StudioReportDetails details = db.reapReportDetails().get(0);

    assertEquals("Histogram", details.getType());
    assertEquals(t1, details.getThreadDumpPath());
    assertEquals(h1, details.getHistogramPath());
    assertEquals("Histogram description", details.getDescription());
  }

  @Test
  public void testPerformanceThreadDumpContent() throws IOException {
    Path t1 = createTempFileWithThreadDump("T1");

    db.appendPerformanceThreadDump(t1, "Performance thread dump description");

    StudioReportDetails details = db.reapReportDetails().get(0);

    assertEquals("PerformanceThreadDump", details.getType());
    assertEquals(t1, details.getThreadDumpPath());
    assertNull(details.getHistogramPath());
    assertEquals("Performance thread dump description", details.getDescription());
  }

  @Test
  public void testCorruptedDatabaseFile() throws IOException {
    Path t1 = createTempFileWithThreadDump("T1");
    db.appendPerformanceThreadDump(t1, "Performance thread dump description");
    Files.write(databaseFile.toPath(), "Corrupted json".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    List<StudioReportDetails> details = db.reapReportDetails();

    // If the db file contains corrupted of malformed json, return no reports.
    assertEquals(0, details.size());

    // Test that database works even after its file gets corrupted.
    Path t2 = createTempFileWithThreadDump("T2");
    db.appendPerformanceThreadDump(t2, "Performance thread dump description");
    details = db.reapReportDetails();

    assertEquals(1, details.size());
    assertEquals(t2, details.get(0).getThreadDumpPath());
  }

  @NotNull
  private Path createTempFileWithThreadDump(@NotNull String contents) throws IOException {
    File file = myTestFolder.newFile();
    Files.write(file.toPath(), contents.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE);
    return file.toPath();
  }
}
