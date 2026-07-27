package com.solar.launcher.globalcontext;

import android.util.Log;

import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompanionUmsShellTest {

    @Test
    public void enable_returnsTrueWhenScriptSucceedsAndExported() throws Exception {
        Process mockProcess = mock(Process.class);
        when(mockProcess.waitFor()).thenReturn(0);
        Runtime mockRuntime = mock(Runtime.class);
        when(mockRuntime.exec(any(String[].class))).thenReturn(mockProcess);

        try (MockedStatic<Runtime> runtimeMock = Mockito.mockStatic(Runtime.class);
             MockedStatic<Log> logMock = Mockito.mockStatic(Log.class);
             MockedStatic<CompanionUmsShell> shellMock = Mockito.mockStatic(CompanionUmsShell.class, Mockito.CALLS_REAL_METHODS);
             MockedConstruction<File> fileMock = Mockito.mockConstruction(File.class, (mock, context) -> {
                 when(mock.isFile()).thenReturn(true);
             })) {

            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            logMock.when(() -> Log.w(anyString(), anyString())).thenReturn(0);
            logMock.when(() -> Log.w(anyString(), anyString(), any(Throwable.class))).thenReturn(0);
            logMock.when(() -> Log.i(anyString(), anyString())).thenReturn(0);

            shellMock.when(CompanionUmsShell::isMassStorageExported).thenReturn(true);

            assertTrue(CompanionUmsShell.enable());
        }
    }

    @Test
    public void enable_returnsFalseWhenScriptFails() throws Exception {
        Process mockProcess = mock(Process.class);
        when(mockProcess.waitFor()).thenReturn(1);
        Runtime mockRuntime = mock(Runtime.class);
        when(mockRuntime.exec(any(String[].class))).thenReturn(mockProcess);

        try (MockedStatic<Runtime> runtimeMock = Mockito.mockStatic(Runtime.class);
             MockedStatic<Log> logMock = Mockito.mockStatic(Log.class);
             MockedStatic<CompanionUmsShell> shellMock = Mockito.mockStatic(CompanionUmsShell.class, Mockito.CALLS_REAL_METHODS);
             MockedConstruction<File> fileMock = Mockito.mockConstruction(File.class, (mock, context) -> {
                 when(mock.isFile()).thenReturn(true);
             })) {

            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            logMock.when(() -> Log.w(anyString(), anyString())).thenReturn(0);
            logMock.when(() -> Log.w(anyString(), anyString(), any(Throwable.class))).thenReturn(0);
            logMock.when(() -> Log.i(anyString(), anyString())).thenReturn(0);

            shellMock.when(CompanionUmsShell::isMassStorageExported).thenReturn(true);

            assertFalse(CompanionUmsShell.enable());
        }
    }

    @Test
    public void enable_returnsFalseWhenScriptSucceedsButNotExported() throws Exception {
        Process mockProcess = mock(Process.class);
        when(mockProcess.waitFor()).thenReturn(0);
        Runtime mockRuntime = mock(Runtime.class);
        when(mockRuntime.exec(any(String[].class))).thenReturn(mockProcess);

        try (MockedStatic<Runtime> runtimeMock = Mockito.mockStatic(Runtime.class);
             MockedStatic<Log> logMock = Mockito.mockStatic(Log.class);
             MockedStatic<CompanionUmsShell> shellMock = Mockito.mockStatic(CompanionUmsShell.class, Mockito.CALLS_REAL_METHODS);
             MockedConstruction<File> fileMock = Mockito.mockConstruction(File.class, (mock, context) -> {
                 when(mock.isFile()).thenReturn(true);
             })) {

            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            logMock.when(() -> Log.w(anyString(), anyString())).thenReturn(0);
            logMock.when(() -> Log.w(anyString(), anyString(), any(Throwable.class))).thenReturn(0);
            logMock.when(() -> Log.i(anyString(), anyString())).thenReturn(0);

            shellMock.when(CompanionUmsShell::isMassStorageExported).thenReturn(false);

            assertFalse(CompanionUmsShell.enable());
        }
    }

    @Test
    public void enable_returnsFalseWhenScriptMissing() throws Exception {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class);
             MockedStatic<CompanionUmsShell> shellMock = Mockito.mockStatic(CompanionUmsShell.class, Mockito.CALLS_REAL_METHODS);
             MockedConstruction<File> fileMock = Mockito.mockConstruction(File.class, (mock, context) -> {
                 when(mock.isFile()).thenReturn(false);
             })) {

            logMock.when(() -> Log.w(anyString(), anyString())).thenReturn(0);

            assertFalse(CompanionUmsShell.enable());
        }
    }
}
