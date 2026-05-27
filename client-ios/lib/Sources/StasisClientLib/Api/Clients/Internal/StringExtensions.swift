extension String {
    var trimmedTrailingSlash: String {
        var result = self
        while result.hasSuffix("/") { result.removeLast() }
        return result
    }
}
