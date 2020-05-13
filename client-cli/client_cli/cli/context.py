"""CLI context container."""


class Context:
    """CLI context container."""

    def __init__(self):
        self.api = None
        self.init = None
        self.service_binary = None
        self.service_main_class = None
        self.filtering = None
        self.sorting = None
        self.rendering = None
