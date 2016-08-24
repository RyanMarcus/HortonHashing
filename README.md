# HortonHashing
This is an open-source implementation of the hash table algorithm described in the Usenix ATC 2016 conference "Horton Tables: Fast Hash Tables for In-Memory Data-Intensive Computing".

The goal of this algorithm is to optimize lookup performance.  The paper compares performance against a more common hash table implementation, bucketized cuckoo hash tables in terms of the average number of buckets examined in lookups.  A bucketized cuckoo hash table examines an average of 1.5 buckets during positive lookups (where the key is in the hash table), and a guaranteed 2 buckets during negative lookups (where the key is not).  The Horton hashing algorithm improves this by introducing a structure called a "remap array entry" which improves these numbers to an average of 1.1 buckets per positive lookup, and 1.08 buckets per negative lookup according to the model presented by the paper.

The paper and presentation are availalble at https://www.usenix.org/node/196285.
