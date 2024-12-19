# deployment

This submodule/directory provides example configuration for development and production purposes.

> For more information, check the README files in each subdirectory.

> There's also a [grafana](grafana/) folder where all dashboards are stored; they are the same for both development and
> production.

## `dev`

Deployment configuration for development purposes with minimal required prep steps.

> :warning: Warning: Only in-memory persistence is available by default! All data is lost on stop/restart
> and applications effectively reset themselves to their original configuration on start.

## `production`

Configuration usable as a foundation for production deployments. Defaults are provided where possible
and configuration that must be filled out by the user is marked as such.

_It should be noted that deploying `stasis` to production is an involved process requiring
a lot of configuration to be provided. For some issues experienced by other users, check
the [troubleshooting issues on GitHub](https://github.com/sndnv/stasis/issues?q=label%3Atroubleshooting)._

> :warning: Warning: It is up to the user to provide a secure environment for these services to run!
> Any advice on how to achieve that is generally out-of-scope for this project.
