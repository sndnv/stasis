package stasis.client_android.sources

class LibraryPermissionMissing(scheme: String) :
    Exception("No permission granted for source [$scheme]")
