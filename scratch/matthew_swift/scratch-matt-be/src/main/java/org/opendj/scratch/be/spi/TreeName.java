package org.opendj.scratch.be.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("javadoc")
public final class TreeName {
    /** Assumes name components don't contain a '/'. */
    public static TreeName of(final String... names) {
        return new TreeName(Arrays.asList(names));
    }

    private final List<String> names;
    private final String s;

    public TreeName(final List<String> names) {
        this.names = names;
        final StringBuilder builder = new StringBuilder();
        for (final String name : names) {
            builder.append('/');
            builder.append(name);
        }
        this.s = builder.toString();
    }

    public TreeName child(final String name) {
        final List<String> newNames = new ArrayList<String>(names.size() + 1);
        newNames.addAll(names);
        newNames.add(name);
        return new TreeName(newNames);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof TreeName) {
            return s.equals(((TreeName) obj).s);
        } else {
            return false;
        }
    }

    public List<String> getNames() {
        return names;
    }

    public TreeName getSuffix() {
        if (names.size() == 0) {
            throw new IllegalStateException();
        }
        return new TreeName(Collections.singletonList(names.get(0)));
    }

    @Override
    public int hashCode() {
        return s.hashCode();
    }

    public boolean isSuffixOf(final TreeName tree) {
        if (names.size() > tree.names.size()) {
            return false;
        }
        for (int i = 0; i < names.size(); i++) {
            if (!tree.names.get(i).equals(names.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return s;
    }
}
