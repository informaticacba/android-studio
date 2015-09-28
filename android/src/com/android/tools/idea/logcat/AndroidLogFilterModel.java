/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.logcat;

import com.android.ddmlib.Log;
import com.android.tools.idea.logcat.AndroidLogcatReceiver.LogMessageHeader;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFilter;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A filter which plugs into {@link LogConsoleBase} for custom logcat filtering.
 * This deliberately drops the custom pattern behaviour of LogFilterModel, replacing it with a new version that allows regex support.
 */
public abstract class AndroidLogFilterModel extends LogFilterModel {

  private final List<LogFilterListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private LogMessageHeader myPrevMessageHeader;
  private Date myRejectBeforeDate;

  private boolean myFullMessageApplicable = false;
  private boolean myFullMessageApplicableByCustomFilter = false;
  private StringBuilder myMessageBuilder = new StringBuilder();
  private @Nullable Pattern myCustomPattern;

  protected List<AndroidLogFilter> myLogFilters = new ArrayList<AndroidLogFilter>();

  public AndroidLogFilterModel() {
    for (Log.LogLevel logLevel : Log.LogLevel.values()) {
      myLogFilters.add(new AndroidLogFilter(logLevel));
    }
  }

  // Implemented because it is abstract in the parent, but the functionality is no longer used.
  @Override
  public String getCustomFilter() {
    return "";
  }

  /**
   * This is called to enable regular expression filtering of log messages.
   * Replaces the customFilter mechanism.
   */
  public void updateCustomPattern(@Nullable Pattern pattern) {
    myCustomPattern = pattern;
    fireTextFilterChange();
  }

  public final void updateConfiguredFilter(@Nullable ConfiguredFilter filter) {
    setConfiguredFilter(filter);
    fireTextFilterChange();
  }

  protected void setConfiguredFilter(@Nullable ConfiguredFilter filter) {
  }

  @Nullable
  protected ConfiguredFilter getConfiguredFilter() {
    return null;
  }

  protected abstract void saveLogLevel(String logLevelName);

  @Override
  public final void addFilterListener(LogFilterListener listener) {
    myListeners.add(listener);
  }

  @Override
  public final void removeFilterListener(LogFilterListener listener) {
    myListeners.remove(listener);
  }

  /**
   * Once called, any logcat messages processed with a timestamp older than our most recent one
   * will be filtered out from now on.
   *
   * This is useful as a way to mark a time where you don't care about older messages. For example,
   * if you change your active filter and replay all logcat messages from the beginning, we will
   * skip over any that were originally reported before we called this method.
   *
   * This can also act as a lightweight clear, in case clearing the logcat buffer on the device
   * fails for some reason (which does happen). If you call this method, clear the console text
   * even without clearing the device's logcat buffer, and reprocess all messages, old messages
   * will be skipped.
   */
  public void beginRejectingOldMessages() {
    if (myPrevMessageHeader == null) {
      return; // Haven't received any messages yet, so nothing to filter
    }

    myRejectBeforeDate = myPrevMessageHeader.getTimeAsDate();
  }


  private void fireTextFilterChange() {
    for (LogFilterListener listener : myListeners) {
      listener.onTextFilterChange();
    }
  }

  private void fireFilterChange(LogFilter filter) {
    for (LogFilterListener listener : myListeners) {
      listener.onFilterStateChange(filter);
    }
  }

  private static Key getProcessOutputType(@NotNull Log.LogLevel level) {
    switch (level) {
      case VERBOSE:
        return AndroidLogcatConstants.VERBOSE;
      case INFO:
        return AndroidLogcatConstants.INFO;
      case DEBUG:
        return AndroidLogcatConstants.DEBUG;
      case WARN:
        return AndroidLogcatConstants.WARNING;
      case ERROR:
        return AndroidLogcatConstants.ERROR;
      case ASSERT:
        return AndroidLogcatConstants.ASSERT;
    }
    return ProcessOutputTypes.STDOUT;
  }

  @Override
  public final boolean isApplicable(String text) {
    // Not calling the super class version, it does not do what we want with regular expression matching
    if (myCustomPattern != null && !myCustomPattern.matcher(text).find()) return false;
    final LogFilter selectedLogLevelFilter = getSelectedLogLevelFilter();
    return selectedLogLevelFilter == null || selectedLogLevelFilter.isAcceptable(text);
  }

  public final boolean isApplicableByCustomFilter(String text) {
    final ConfiguredFilter configuredFilterName = getConfiguredFilter();
    if (configuredFilterName == null) {
      return true;
    }

    Log.LogLevel logLevel = null;
    String tag = null;
    String pkg = null;
    String pid = null;
    String message = text;

    AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(text);
    if (result.getHeader() != null) {
      LogMessageHeader header = result.getHeader();
      logLevel = header.myLogLevel;
      tag = header.myTag;
      pkg = header.myAppPackage;
      pid = Integer.toString(header.myPid);
      message = result.getMessage();
    }
    else if (myPrevMessageHeader != null) {
      tag = myPrevMessageHeader.myTag;
      pkg = myPrevMessageHeader.myAppPackage;
      pid = Integer.toString(myPrevMessageHeader.myPid);
      logLevel = myPrevMessageHeader.myLogLevel;
    }

    return configuredFilterName.isApplicable(message, tag, pkg, pid, logLevel);
  }

  @Override
  public final List<? extends LogFilter> getLogFilters() {
    return myLogFilters;
  }

  private final class AndroidLogFilter extends LogFilter {
    final Log.LogLevel myLogLevel;

    private AndroidLogFilter(Log.LogLevel logLevel) {
      super(StringUtil.capitalize(logLevel.name().toLowerCase()));
      myLogLevel = logLevel;
    }

    @Override
    public boolean isAcceptable(String line) {
      Log.LogLevel logLevel = null;

      AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(line);
      if (result.getHeader() != null) {
        logLevel = result.getHeader().myLogLevel;
      }
      else if (myPrevMessageHeader != null) {
        logLevel = myPrevMessageHeader.myLogLevel;
      }
      return logLevel != null && logLevel.getPriority() >= myLogLevel.getPriority();
    }
  }

  public abstract String getSelectedLogLevelName();

  @Nullable
  private LogFilter getSelectedLogLevelFilter() {
    final String filterName = getSelectedLogLevelName();
    if (filterName != null) {
      for (AndroidLogFilter logFilter : myLogFilters) {
        if (filterName.equals(logFilter.myLogLevel.name())) {
          return logFilter;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isFilterSelected(LogFilter filter) {
    return filter == getSelectedLogLevelFilter();
  }

  @Override
  public void selectFilter(LogFilter filter) {
    if (!(filter instanceof AndroidLogFilter)) {
      return;
    }
    String newFilterName = ((AndroidLogFilter)filter).myLogLevel.name();
    if (!Comparing.equal(newFilterName, getSelectedLogLevelName())) {
      saveLogLevel(newFilterName);
      fireFilterChange(filter);
    }
  }

  @Override
  public void processingStarted() {
    myPrevMessageHeader = null;
    myFullMessageApplicable = false;
    myFullMessageApplicableByCustomFilter = false;
    myMessageBuilder.setLength(0);
  }

  @Override
  @NotNull
  public final MyProcessingResult processLine(String line) {
    AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(line);
    final boolean hasHeader = result.getHeader() != null;

    if (hasHeader) {
      myPrevMessageHeader = result.getHeader();
    }
    final boolean applicable = isApplicable(line);
    final boolean applicableByCustomFilter = isApplicableByCustomFilter(line);

    String messagePrefix;

    if (hasHeader) {
      messagePrefix = null;
      myMessageBuilder.setLength(0);
      myMessageBuilder.append(line).append('\n');
      myFullMessageApplicable = applicable;
      myFullMessageApplicableByCustomFilter = applicableByCustomFilter;
    }
    else {
      messagePrefix = (myFullMessageApplicable || applicable) &&
                      (myFullMessageApplicableByCustomFilter || applicableByCustomFilter) &&
                      !(myFullMessageApplicable && myFullMessageApplicableByCustomFilter) ? myMessageBuilder.toString() : null;
      myMessageBuilder.append(line).append('\n');
      myFullMessageApplicable = myFullMessageApplicable || applicable;
      myFullMessageApplicableByCustomFilter = myFullMessageApplicableByCustomFilter || applicableByCustomFilter;
    }
    final Key key = myPrevMessageHeader != null ? getProcessOutputType(myPrevMessageHeader.myLogLevel) : ProcessOutputTypes.STDOUT;

    boolean isApplicable = myFullMessageApplicable && myFullMessageApplicableByCustomFilter;
    if (isApplicable && myRejectBeforeDate != null && myPrevMessageHeader != null) {
      isApplicable = myPrevMessageHeader.getTimeAsDate().after(myRejectBeforeDate);
    }

    return new MyProcessingResult(key, isApplicable, messagePrefix);
  }
}
