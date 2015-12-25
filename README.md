strace-analyzer
---------------

Analyzes [strace][] output. Currently, the following analysis commands are provided:

- **summary** (default) short summary
- **read** per file read summary
- **write** per file write summary
- **io** does both **read** and **write**

File descriptors are associated with file names. The association is made when syscalls like
**open**, **creat**, **dup** or **pipe** are read from the log and the association gets terminated
when they get **close**d.

usage
-----

```bash
# create logs
strace -T -ttt -ff -o strace.log command

# analyze logs (command defines how they are analyzed)
strace-analyzer <command> strace.log.4242 strace.log.4243

# do stuff with the output
strace-analyzer read strace.log.27049 | sort -h -k 2 | column -t
```

More help can be found here:

```bash
strace --help
```

caveats
-------

-   does only work with traces created with the usage example above, there is no support for logs
    that contain output of multiple process ids

-   does not parse unfinished / resumed entries, single-threaded application runs are recommended or
    else you are going to miss a lot of entries

issues, features, use-cases, wish list
--------------------------------------

-   If you think of a new (possibly high-level) analysis use case or how to improve an existing one,
    please [open an issue][newissue]. If you have an idea on how the output should look like, feel
    free to include a sketch of an example.

-   If you recognize missing file associations in the output, i.e. bare file descriptor numbers
    without a note as to why it could not be identified, please [open an issue][newissue] and
    provide access to that particular, complete strace log, so I am able to identify the problem.

    If you know that a particular file should be included, because you know that file has been
    opened, you could also try to identify the issue yourself by following this workflow:

    1.  Search for the file name and identify the file descriptor associated with it:

            $ grep filename strace.log.32029
            1451071295.259192 execve("/usr/bin/dd", ["dd", "if=filename", "of=/dev/null"], [/* 37 vars */]) = 0 <0.000347>
            1451071295.262536 open("filename", O_RDONLY) = 3 <0.000023>

        In this example, **filename** gets **open**'ed with file descriptor **3**.

    2.  Search for the previously identified file descriptor, while already filtering syscalls that
        will not help in the search, like **read** and **write**:

            $ grep -w 3 strace.log.32029 | grep -vwE "read|write|mmap|fstat"
            1451071295.259970 open("/etc/ld.so.cache", O_RDONLY|O_CLOEXEC) = 3 <0.000024>
            1451071295.260165 close(3)              = 0 <0.000014>
            1451071295.260238 open("/usr/lib/libc.so.6", O_RDONLY|O_CLOEXEC) = 3 <0.000026>
            1451071295.260824 close(3)              = 0 <0.000016>
            1451071295.262157 open("/usr/lib/locale/locale-archive", O_RDONLY|O_CLOEXEC) = 3 <0.000030>
            1451071295.262374 close(3)              = 0 <0.000017>
            1451071295.262536 open("filename", O_RDONLY) = 3 <0.000023>
            1451071295.262609 dup2(3, 0)            = 0 <0.000017>
            1451071295.262666 close(3)              = 0 <0.000146>
            1451071295.262913 open("/dev/null", O_WRONLY|O_CREAT|O_TRUNC, 0666) = 3 <0.000031>
            1451071295.262988 dup2(3, 1)            = 1 <0.000015>
            1451071295.263039 close(3)              = 0 <0.000015>

        You should search for the section *after **filename** gets opened and before its file
        descriptor gets closed*.

        In this example, there is only a single syscall between the respective **open** and
        **close** syscalls, namely **dup2**.

    Using this workflow, I had identified that **dup2** was a syscall relevant to file descriptor to
    file name association which I had not handled yet. I added **dup2** to the list of handled
    syscalls and the file associations work now.

features that will not be implemented
-------------------------------------

In the spirit of the Unix philosohpy of **do one thing and do it well**, strace-analyzer will not do
any of the following:

-   filtering, use tools like [grep][] or [awk][], e.g.:

        strace-analyzer read strace.log.1835 | grep scala
        strace-analyzer read strace.log.1835 | awk '/scala/'

-   sorting, use the [sort][] command line utility, e.g.:

        strace-analyzer read strace.log.27049 | sort -h -k 2

-   pretty tabular output printing, use the [column][] command line utility, e.g.:

        strace-analyzer read strace.log.27049 | column -t

[awk]: http://man7.org/linux/man-pages/man1/gawk.1.html "gawk man page"
[grep]: http://man7.org/linux/man-pages/man1/grep.1.html "grep man page"
[column]: http://man7.org/linux/man-pages/man1/column.1.html "column man page"
[newissue]: https://github.com/wookietreiber/strace-analyzer/issues/new "open new issue"
[sort]: http://man7.org/linux/man-pages/man1/sort.1.html "sort man page"
[strace]: http://sourceforge.net/projects/strace/ "strace home page"
