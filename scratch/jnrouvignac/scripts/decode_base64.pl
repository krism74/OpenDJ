#!/usr/bin/perl -w

use warnings;
use strict;
use MIME::Base64 ();

sub base64_decode() {
    print "="x80 . "\n";
    print "base64:: $_\n\n";
    print "".MIME::Base64::decode($_)."\n";
}

if (@ARGV > 0) {
    # read all args then exit
    foreach (@ARGV) {
        base64_decode();
    }
} else {
    # read from stdin
    while (<>) {
        base64_decode();
    }
}


