package com.balsikandar.crashreporter.utils;

/**
 * Created by bali on 02/08/17.
 */

/**
 * An Exception indicating that the Crash Reporter has not been correctly initialized.
 */
public class CrashReporterNotInitializedException extends CrashReporterException {
    static final long serialVersionUID = 1;

    /**
     * Constructs a CrashReporterNotInitializedException with no additional information.
     */
    public CrashReporterNotInitializedException() {
        super();
    }

    /**
     * Constructs a CrashReporterNotInitializedException with a message.
     *
     * @param message A String to be returned from getMessage.
     */
    public CrashReporterNotInitializedException(String message) {
        super(message);
    }

    /**
     * Constructs a CrashReporterNotInitializedException with a message and inner error.
     *
     * @param message   A String to be returned from getMessage.
     * @param throwable A Throwable to be returned from getCause.
     */
    public CrashReporterNotInitializedException(String message, Throwable throwable) {
        super(message, throwable);
    }

    /**
     * Constructs a CrashReporterNotInitializedException with an inner error.
     *
     * @param throwable A Throwable to be returned from getCause.
     */
    public CrashReporterNotInitializedException(Throwable throwable) {
        super(throwable);
    }
}