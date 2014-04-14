#!/usr/bin/perl -w

use warnings;
use strict;
use Math::BigInt;

sub decode_csn() {
    my $timeStamp = Math::BigInt->new('0x'.substr($_,  0, 16));
    my $date = localtime($timeStamp);
    my $serverId  = hex(substr($_, 16,  4));
    my $seqnum    = hex(substr($_, 20,  8));
    print "CSN=$_\ttimeStamp=$timeStamp ($date), serverId=$serverId, seqnum=$seqnum\n";
}

if (@ARGV > 0) {
    # read all args then exit
    foreach (@ARGV) {
        decode_csn();
    }
} else {
    # read from stdin
    while (<>) {
        decode_csn();
    }
}


