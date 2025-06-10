# deployment

This submodule/directory provides example configuration for development and production purposes.

> For more information, check the README files in each subdirectory.

> There's also a [grafana](grafana/) folder where all dashboards are stored; they are the same for both development and
> production.

## `dev`

Deployment configuration for development purposes with minimal required prep steps.

> :warning: Warning: Only in-memory persistence is available by default! All data is lost on stop/restart
> and applications effectively reset themselves to their original configuration on start.

### Known Issues

#### Running on Apple Silicon
The following message may appear when running the images:
```
[server]      |
[server]      | No java installations was detected.
[server]      | Please go to http://www.java.com/getjava/ and download
[server]      |
```

And running `java --version` in the container results in (something along the lines of):
```
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGILL (0x4) at pc=0x0000ffff87d3fc5c, pid=9, tid=10
#
# ...
#
[0.019s][warning][os] Loading hsdis library failed
#
# The crash happened outside the Java Virtual Machine in native code.
# See problematic frame for where to report the bug.
#
Aborted (core dumped)
```

The workaround is to use `-XX:UseSVE=0` when starting the JVM, however, the runner script currently does not pass JVM options
to the JVM check (resulting in the above `No java installations was detected` message). To go around that, the JVM check can
be disabled:

```
  server:
    image: ghcr.io/sndnv/stasis/stasis-server:dev-latest
    entrypoint: /opt/docker/bin/stasis-server -no-version-check # disables the jvm/version check
    ...
    environment:
      - JAVA_OPTS=-XX:UseSVE=0 # provides the UseSVE config to the JVM
      ...
```

## `production`

Configuration usable as a foundation for production deployments. Defaults are provided where possible
and configuration that must be filled out by the user is marked as such.

_It should be noted that deploying `stasis` to production is an involved process requiring
a lot of configuration to be provided. For some issues experienced by other users, check
the [troubleshooting issues on GitHub](https://github.com/sndnv/stasis/issues?q=label%3Atroubleshooting)._

> :warning: Warning: It is up to the user to provide a secure environment for these services to run!
> Any advice on how to achieve that is generally out-of-scope for this project.
