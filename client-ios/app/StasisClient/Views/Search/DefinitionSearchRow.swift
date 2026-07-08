import Foundation
import StasisClientLib

struct DefinitionSearchRow: Identifiable, Equatable {
    let definition: DatasetDefinitionId
    let result: DatasetDefinitionResult

    var id: DatasetEntryId { result.entryId }
}
