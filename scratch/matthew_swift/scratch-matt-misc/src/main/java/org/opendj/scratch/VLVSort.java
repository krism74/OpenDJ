package org.opendj.scratch;

import static java.util.Arrays.asList;

import java.io.UnsupportedEncodingException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteStringBuilder;

/**
 *
 */
public final class VLVSort {

    public static void main(String[] args) throws Exception {
        // @formatter:off
        String[][] keys = {
            { "a",  "a", "a" },
            { "a",  "a", "b" },
            { "a",  "b", "a" },
            { "a",  "b", "b" },
            { "b",  "a", "a" },
            { "b",  "a", "b" },
            { "b",  "b", "a" },
            { "b",  "b", "b" },
            { "aa",  "aa", "aa" },
            { "aa",  "aa", "bb" },
            { "aa",  "bb", "aa" },
            { "aa",  "bb", "bb" },
            { "bb",  "aa", "aa" },
            { "bb",  "aa", "bb" },
            { "bb",  "bb", "aa" },
            { "bb",  "bb", "bb" },
            { "aa",  "a", "a" },
            { "aa",  "a", "b" },
            { "aa",  "b", "a" },
            { "aa",  "b", "b" },
            { "bb",  "a", "a" },
            { "bb",  "a", "b" },
            { "bb",  "b", "a" },
            { "bb",  "b", "b" },
        };
        // @formatter:on

        TreeSet<String[]> sorted = new TreeSet<>(new Comparator<String[]>() {
            @Override
            public int compare(String[] o1, String[] o2) {
                // ascending
                int r1 = o1[0].compareTo(o2[0]);
                if (r1 != 0) {
                    return r1;
                }

                // descending
                int r2 = o1[1].compareTo(o2[1]);
                if (r2 != 0) {
                    return -r2;
                }

                // ascending
                return o1[2].compareTo(o2[2]);
            }
        });

        TreeMap<byte[], String[]> sortedByBytes = new TreeMap<>(new Comparator<byte[]>() {
            public int compare(byte[] o1, byte[] o2) {
                int sz = Math.min(o1.length, o2.length);
                for (int i = 0; i < sz; i++) {
                    int v1 = 0xFF & o1[i];
                    int v2 = 0xFF & o2[i];
                    if (v1 != v2) {
                        return v1 - v2;
                    }
                }
                return o1.length - o2.length;
            };
        });

        for (String[] s : keys) {
            sorted.add(s);
            sortedByBytes.put(normalize(s), s);
        }

        Iterator<String[]> i1 = sorted.iterator();
        Iterator<String[]> i2 = sortedByBytes.values().iterator();
        while (i1.hasNext()) {
            final List<String> expected = asList(i1.next());
            final List<String> actual = asList(i2.next());
            final String result = expected.equals(actual) ? "GOOD" : "BAD ";

            System.out.println(result + " " + expected + " --> " + actual);
        }
    }

    private static byte[] normalize(String[] s) throws UnsupportedEncodingException {
        ByteStringBuilder builder = new ByteStringBuilder();
        normalize(builder, s[0].getBytes("UTF-8"), true);
        normalize(builder, s[1].getBytes("UTF-8"), false);
        normalize(builder, s[2].getBytes("UTF-8"), true);
        return builder.toByteArray();
    }

    private static void normalize(ByteStringBuilder builder, final byte[] bytes, boolean ascending) {
        byte separator = ascending ? 0 : (byte) 0xff;
        byte escape = ascending ? 1 : (byte) 0xfe;
        byte xor = separator;
        for (byte b : bytes) {
            if (b == separator || b == escape) {
                builder.append(escape);
            }
            final byte bxor = (byte) (b ^ separator);
            builder.append(bxor);
        }
        builder.append(separator);
    }
}
