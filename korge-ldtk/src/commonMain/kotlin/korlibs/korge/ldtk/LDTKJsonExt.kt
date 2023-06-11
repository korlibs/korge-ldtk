package korlibs.korge.ldtk

import korlibs.datastructure.Extra
import korlibs.math.geom.Anchor
import korlibs.math.geom.PointInt
import kotlinx.serialization.json.jsonPrimitive

val EntityInstance.gridPos: PointInt get() = PointInt(grid[0], grid[1])
val EntityInstance.pivotAnchor: Anchor get() = Anchor(pivot[0], pivot[1])
val EntityDefinition.fieldDefsByName by Extra.PropertyThis { this.fieldDefs.associateBy { it.identifier } }
val Definitions.entitiesByUid by Extra.PropertyThis { this.entities.associateBy { it.uid } }
operator fun EntityDefinition.get(name: String): FieldDefinition? = fieldDefsByName[name]
fun FieldInstance.definition(ldtk: LDTKJson): FieldDefinition {
    return ldtk.defs.levelFields[this.defUid]
}
data class FieldInfo(val def: FieldDefinition, val instance: FieldInstance) {
    val identifier = def.identifier

    val valueString: String? get() = instance.value?.jsonPrimitive?.content
}