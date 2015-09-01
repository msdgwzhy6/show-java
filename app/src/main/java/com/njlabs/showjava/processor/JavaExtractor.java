package com.njlabs.showjava.processor;

import com.crashlytics.android.Crashlytics;
import com.njlabs.showjava.utils.SourceInfo;
import com.njlabs.showjava.utils.logging.Ln;

import org.benf.cfr.reader.Main;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import java.io.File;

import jadx.api.JadxDecompiler;
import jadx.core.utils.exceptions.JadxException;

/**
 * Created by Niranjan on 29-05-2015.
 */
public class JavaExtractor extends ProcessServiceHelper {

    public JavaExtractor(ProcessService processService) {
        this.processService = processService;
        this.UIHandler = processService.UIHandler;
        this.packageFilePath = processService.packageFilePath;
        this.packageName = processService.packageName;
        this.exceptionHandler = processService.exceptionHandler;
        this.sourceOutputDir = processService.sourceOutputDir;
        this.javaSourceOutputDir = processService.javaSourceOutputDir;
    }

    public void extract() {

        broadcastStatus("jar2java");

        final File dexInputFile = new File(sourceOutputDir + "/optimised_classes.dex");
        final File jarInputFile = new File(sourceOutputDir + "/" + packageName + ".jar");

        final File javaOutputDir = new File(javaSourceOutputDir);

        if (!javaOutputDir.isDirectory()) {
            javaOutputDir.mkdirs();
        }

        ThreadGroup group = new ThreadGroup("Jar 2 Java Group");
        Thread javaExtractionThread = new Thread(group, new Runnable() {
            @Override
            public void run() {
                boolean javaError = false;
                try {
                    switch (processService.decompilerToUse) {
                        case "jadx":
                            decompileWithJaDX(dexInputFile, javaOutputDir);
                            break;
                        case "procyon":
                            decompileWithProcyon(jarInputFile);
                            break;
                        case "fernflower":
                            decompilerWithFernFlower(jarInputFile);
                            break;
                        default:
                            decompileWithCFR(jarInputFile, javaOutputDir);
                    }
                } catch (Exception | StackOverflowError e){
                    Ln.e(e);
                    javaError = true;
                }
                startXMLExtractor(!javaError);
            }
        }, "Jar to Java Thread", processService.STACK_SIZE);
        javaExtractionThread.setPriority(Thread.MAX_PRIORITY);
        javaExtractionThread.setUncaughtExceptionHandler(exceptionHandler);
        javaExtractionThread.start();

    }

    private void decompileWithCFR(File jarInputFile, File javaOutputDir){
        String[] args = {jarInputFile.toString(), "--outputdir", javaOutputDir.toString()};
        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        try {
            options = getOptParser.parse(args, OptionsImpl.getFactory());
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        final DCCommonState dcCommonState = new DCCommonState(options);
        final String path = options != null ? options.getFileName() : null;

        Main.doJar(dcCommonState, path);
    }

    private void decompileWithProcyon(File jarInputFile) {
        // TODO ADD PROCYON (make it work)
        /*String[] args = {"-jar", jarInputFile.toString(), "-o", javaSourceOutputDir};
        DecompilerDriver.main(args);*/
    }

    private void decompilerWithFernFlower(File jarInputFile) {
        String[] args = {jarInputFile.toString(), javaSourceOutputDir};
        ConsoleDecompiler.main(args);
    }

    private void decompileWithJaDX(final File dexInputFile, final File javaOutputDir) throws JadxException {
        JadxDecompiler jadx = new JadxDecompiler();
        jadx.setOutputDir(javaOutputDir);
        jadx.loadFile(dexInputFile);
        jadx.save();
    }

    private void startXMLExtractor(boolean hasJava) {
        SourceInfo.setjavaSourceStatus(processService, hasJava);
        ((new ResourcesExtractor(processService))).extract();
    }
}
