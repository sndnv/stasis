package stasis.client_android.sources

data class EntityPreview(val sections: List<Section>) {
    data class Section(val title: String, val fields: List<Field>)

    data class Field(val label: String, val value: String)
}
