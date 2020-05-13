"""Interface for managing interactions between a client frontend (this CLI) and backend (API)."""

from abc import ABC, abstractmethod


class ClientApi(ABC):
    """Interface for managing interactions between a client frontend (this CLI) and backend (API)."""

    @abstractmethod
    def is_active(self):
        """
        Checks if the API is active.

        :return: True, if the API is running and responding to requests
        """

    @abstractmethod
    def stop(self):
        """
        Stops the backend service.

        :return: dict with result of action
        """

    @abstractmethod
    def dataset_metadata(self, entry):
        """
        Retrieves dataset metadata for the specified entry.

        :param entry: entry associated with requested metadata
        :return: requested metadata
        """

    @abstractmethod
    def dataset_metadata_search(self, search_query, until):
        """
        Applies the provided search query to the metadata of the client, for all entries until the provided timestamp.

        :param search_query: query to apply
        :param until: timestamp to use for limiting search
        :return: search results
        """

    @abstractmethod
    def dataset_definitions(self):
        """
        Retrieves all dataset definitions for the current user and device.

        :return: requested definitions
        """

    @abstractmethod
    def dataset_entries(self):
        """
        Retrieves all dataset entries.

        :return: requested entries
        """

    @abstractmethod
    def dataset_entries_for_definition(self, definition):
        """
        Retrieves all dataset entries for the provided dataset definition.

        :param definition: definition associated with requested entries
        :return: requested entries
        """

    @abstractmethod
    def user(self):
        """
        Retrieves information about the current user.

        :return: current user
        """

    @abstractmethod
    def device(self):
        """
        Retrieves information about the current device.

        :return: current device
        """

    @abstractmethod
    def device_connections(self):
        """
        Retrieves information about the server connections of the current device.

        :return: active device connections
        """

    @abstractmethod
    def operations(self):
        """
        Retrieves the currently active operations.

        :return: active operations
        """

    @abstractmethod
    def operation_progress(self, operation):
        """
        Retrieves the progress of an operation.

        :param operation: operation to follow
        :return: response as an event stream
        """

    @abstractmethod
    def operation_follow(self, operation):
        """
        Follows an operation's progress.

        :param operation: operation to follow
        :return: response as an event stream
        """

    @abstractmethod
    def operation_stop(self, operation):
        """
        Stops an active operation.

        :param operation: operation to stop
        :return: dict with result of action
        """

    @abstractmethod
    def backup_rules(self):
        """
        Retrieves the current backup specification/rules.

        :return: backup spec/rules
        """

    @abstractmethod
    def backup_start(self, definition):
        """
        Starts a backup for the specified dataset definition.

        :param definition: definition for which to start a backup
        :return: dict with result of action
        """

    @abstractmethod
    def backup_define(self, request):
        """
        Creates a new dataset definition with the provided data.

        :param request: data to use for creating a definition
        :return: dict with result of action
        """

    @abstractmethod
    def recover_until(self, definition, until, path_query, destination, discard_paths):
        """
        Starts a recovery for the specified dataset definition, restricted by the provided timestamp and query.

        :param definition: definition for which to start a recovery
        :param until: timestamp to use for limiting recovery
        :param path_query: file/path query to use for limiting recovery
        :param destination: recovery directory path override
        :param discard_paths: set to True to discard original file directory structure
        :return: dict with result of action
        """

    @abstractmethod
    def recover_from(self, definition, entry, path_query, destination, discard_paths):
        """
        Starts a recovery for the specified dataset definition and entry, restricted by the provided query.

        :param definition: definition for which to start a recovery
        :param entry: entry for which to use for recovery
        :param path_query: file/path query to use for limiting recovery
        :param destination: recovery directory path override
        :param discard_paths: set to True to discard original file directory structure
        :return: dict with result of action
        """

    @abstractmethod
    def recover_from_latest(self, definition, path_query, destination, discard_paths):
        """
        Starts a recovery for the specified dataset definition and latest entry, restricted by the provided query.

        :param definition: definition for which to start a recovery
        :param path_query: file/path query to use for limiting recovery
        :param destination: recovery directory path override
        :param discard_paths: set to True to discard original file directory structure
        :return: dict with result of action
        """

    @abstractmethod
    def schedules_public(self):
        """
        Retrieves all available public schedules.

        :return: requested public schedules
        """

    @abstractmethod
    def schedules_configured(self):
        """
        Retrieves all available configured schedules.

        :return: requested configured schedules
        """

    @abstractmethod
    def schedules_configured_refresh(self):
        """
        Refreshes settings for all configured schedules.

        :return: dict with result of action
        """
