import Foundation
import SwiftData

public enum PersistenceSchema {
    public static let models: [any PersistentModel.Type] = [
        RuleEntity.self,
        ActiveScheduleEntity.self,
        LocalScheduleEntity.self
    ]

    public static func defaultContainer() throws -> ModelContainer {
        try ModelContainer(
            for: Schema(models),
            configurations: ModelConfiguration(
                schema: Schema(models),
                isStoredInMemoryOnly: false
            )
        )
    }

    public static func inMemoryContainer() throws -> ModelContainer {
        try ModelContainer(
            for: Schema(models),
            configurations: ModelConfiguration(
                schema: Schema(models),
                isStoredInMemoryOnly: true
            )
        )
    }
}
