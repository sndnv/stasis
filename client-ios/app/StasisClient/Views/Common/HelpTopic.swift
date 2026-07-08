enum HelpTopic: CaseIterable {
    case home
    case backupDefinitions
    case definitionDetails
    case entryDetails
    case definitionForm
    case recover
    case search
    case operations
    case operationDetails
    case status
    case rules
    case schedules

    var message: String {
        switch self {
        case .home:
            """
            A quick overview of your backups.

            The cards show your most recent backup and the last operation that ran — tap either one to see \
            its details.

            Use Start Backup to run a backup of your default definition right away.
            """
        case .backupDefinitions:
            """
            Showing all available backup definitions.

            Before you can back up, a definition needs to be created; each backup entry is associated with one.

            Backup definitions group and configure backups — how many copies to keep, for how long and how to version them.

            Touch and hold a definition to see the available actions.
            """
        case .definitionDetails:
            """
            Showing all backup entries for the selected definition.

            Each item is an individual backup that was performed, showing when it was done, how much data was stored \
            and what changed since the last backup.

            Touch and hold an entry to see the available actions.
            """
        case .entryDetails:
            """
            Showing the backup metadata for the selected entry.

            Each item is an individual piece of information that was backed up (a photo, file, directory and so on) \
            and shows what changed since the last backup.

            Tap an item for more details, and to preview, save or export it.
            """
        case .definitionForm:
            """
            Creating or updating a backup definition.

            For each definition you can set the retention policies for existing and removed file versions.

            Retention policies:
            • at-most — keep at most a set number of versions
            • latest-only — keep only the latest version
            • all — keep all versions
            """
        case .recover:
            """
            Configuring and starting a recovery.

            How recovery works:
            • unchanged items are left as they are
            • changed items are reset to the backed-up version
            • deleted items are recreated

            Use “Restore From” to choose which kinds of data to recover (files, photos, contacts, calendar).

            Where restored data goes:
            • files are restored to their original locations
            • contacts are merged into the device’s contacts
            • calendar events are restored into the calendar they came from, or your primary calendar; if that calendar \
            syncs to an account, the change may reach your other devices

            To keep a portable copy of a single contact or event, open the matching backup entry and use “Save as…” on it.
            """
        case .search:
            """
            Searching the available backup metadata for entries matching the provided query (a regular expression).

            Results can be restricted to entries up to the provided timestamp.
            """
        case .operations:
            """
            Showing and controlling operations.

            Each item is an individual operation, showing its current state and how many stages or steps have \
            already been performed.

            Touch and hold an operation to see the available actions.
            """
        case .operationDetails:
            """
            Showing operation details.

            An operation is made up of individual stages, and each stage can have one or more steps. Any failures \
            or errors are shown here too.
            """
        case .status:
            """
            Showing the current user and device details, along with the client’s connection state.
            """
        case .rules:
            """
            Showing the sources included in your backups.

            Turn a source on to include it — Photos, Contacts or Calendar — or off to exclude it.

            Only enabled sources are considered when performing backups.
            """
        case .schedules:
            """
            Showing and managing scheduling.

            Each item is a schedule provided by the server or defined locally. Create one or more assignments for a \
            schedule so the client runs the specified actions at the appropriate time.

            Expand a schedule to see its assignments.

            Schedules are ordered by their next execution time, with those to run sooner at the top. If the next \
            schedule has no assignment, no action is taken.
            """
        }
    }
}
