enum BootstrapStep: Hashable {
    case provideServer
    case provideUsername
    case providePassword
    case provideSecret
    case provideCode
}
