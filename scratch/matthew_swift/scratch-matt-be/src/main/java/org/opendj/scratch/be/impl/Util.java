package org.opendj.scratch.be.impl;

import java.io.File;

final class Util {
    static void clearAndCreateDbDir(final File dbDir) {
        if (dbDir.exists()) {
            for (final File child : dbDir.listFiles()) {
                child.delete();
            }
        } else {
            dbDir.mkdirs();
        }
    }

    private Util() {
        // Util class.
    }
}
