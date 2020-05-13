"""Interface for initializing the backend service."""

from abc import ABC, abstractmethod


class InitApi(ABC):
    """Interface for initializing the backend service."""

    @abstractmethod
    def state(self):
        """
        Retrieves the current initialization state.

        :return: requested state
        """

    @abstractmethod
    def provide_credentials(self, username, password):
        """
        Provides the specified credentials to the init API.

        :return: dict with result of action
        """
