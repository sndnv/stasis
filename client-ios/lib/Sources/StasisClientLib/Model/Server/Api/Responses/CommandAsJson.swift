import Foundation

public struct CommandAsJson: Sendable, Equatable, Hashable, Codable {
    public let sequenceId: Int64
    public let source: String
    public let target: UUID?
    public let parameters: CommandParametersAsJson
    public let created: Date

    public init(
        sequenceId: Int64,
        source: String,
        target: UUID?,
        parameters: CommandParametersAsJson,
        created: Date
    ) {
        self.sequenceId = sequenceId
        self.source = source
        self.target = target
        self.parameters = parameters
        self.created = created
    }

    public struct CommandParametersAsJson: Sendable, Equatable, Hashable, Codable {
        public let logoutUser: LogoutUserCommandAsJson?

        public init(logoutUser: LogoutUserCommandAsJson? = nil) {
            self.logoutUser = logoutUser
        }

        public var isEmpty: Bool { logoutUser == nil }
    }

    public struct LogoutUserCommandAsJson: Sendable, Equatable, Hashable, Codable {
        public let reason: String?

        public init(reason: String?) {
            self.reason = reason
        }
    }
}

extension CommandAsJson.CommandParametersAsJson {
    private enum CodingKeys: String, CodingKey {
        case commandType
        case logoutUser
    }

    private enum CommandType: String, Codable {
        case logoutUser = "logout_user"
        case empty
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let commandType = try container.decode(CommandType.self, forKey: .commandType)
        switch commandType {
        case .logoutUser:
            let logoutUser = try container.decode(
                CommandAsJson.LogoutUserCommandAsJson.self,
                forKey: .logoutUser
            )
            self.init(logoutUser: logoutUser)
        case .empty:
            self.init(logoutUser: nil)
        }
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        if let logoutUser {
            try container.encode(CommandType.logoutUser, forKey: .commandType)
            try container.encode(logoutUser, forKey: .logoutUser)
        } else {
            try container.encode(CommandType.empty, forKey: .commandType)
        }
    }
}
