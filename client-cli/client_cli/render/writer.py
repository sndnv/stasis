"""Interface for rendering data retrieved by a client frontend (this CLI)."""

from abc import ABC, abstractmethod


class Writer(ABC):
    """Interface for rendering data retrieved by a client frontend (this CLI)."""

    @abstractmethod
    def render_dataset_definitions(self, definitions) -> str:
        """
        Renders the provided dataset definitions.

        :param definitions: definitions to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_dataset_entries(self, entries) -> str:
        """
        Renders the provided dataset entries.

        :param entries: entries to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_dataset_metadata_changes(self, metadata) -> str:
        """
        Renders the provided changes metadata.

        :param metadata: metadata to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_dataset_metadata_crates(self, metadata) -> str:
        """
        Renders the provided crates metadata.

        :param metadata: metadata to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_dataset_metadata_filesystem(self, metadata) -> str:
        """
        Renders the provided filesystem metadata.

        :param metadata: metadata to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_dataset_metadata_search_result(self, search_result) -> str:
        """
        Renders the provided metadata search result.

        :param search_result: result to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_device(self, device) -> str:
        """
        Renders the provided device.

        :param device: device to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_device_connections(self, connections) -> str:
        """
        Renders the provided device connections.

        :param connections: connections to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_device_commands(self, commands) -> str:
        """
        Renders the provided device commands.

        :param commands: commands to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_operations(self, operations) -> str:
        """
        Renders the provided active operations.

        :param operations: operations to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_operation_progress(self, progress) -> str:
        """
        Renders the provided operation progress.

        :param progress: operation progress to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_backup_rules(self, rules) -> str:
        """
        Renders the provided backup rules.

        :param rules: rules to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_backup_specification_matched(self, state, spec) -> str:
        """
        Renders the provided matched backup specification.

        :param state: specification state (included or excluded)
        :param spec: specification to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_backup_specification_unmatched(self, spec) -> str:
        """
        Renders the provided unmatched backup specification.

        :param spec: specification to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_operation_response(self, response):
        """
        Renders the provided operation response.

        :param response: response to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_public_schedules(self, public_schedules) -> str:
        """
        Renders the provided public schedules.

        :param public_schedules: schedules to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_configured_schedules(self, configured_schedules) -> str:
        """
        Renders the provided configured schedules.

        :param configured_schedules: schedules to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_user(self, user) -> str:
        """
        Renders the provided user.

        :param user: user to render
        :return: render result, as a string
        """

    @abstractmethod
    def render_analytics_state(self, state) -> str:
        """
        Renders the provided analytics state.

        :param state: analytics state to render
        :return: render result, as a string
        """
