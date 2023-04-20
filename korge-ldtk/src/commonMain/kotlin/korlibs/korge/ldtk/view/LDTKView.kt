package korlibs.korge.ldtk.view

import korlibs.datastructure.*
import korlibs.memory.*
import korlibs.korge.ldtk.*
import korlibs.korge.view.*
import korlibs.korge.view.tiles.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.image.tiles.*
import korlibs.io.file.*

class LDTKView(
    val world: LDTKWorld
) : Container() {
    init {
        val ldtk = world.ldtk
        val layersDefsById = world.layersDefsById
        val tilesetDefsById = world.tilesetDefsById
        container {
            for (level in ldtk.levels) {
                container {
                    val color = Colors[level.levelBgColor ?: ldtk.bgColor]
                    solidRect(level.pxWid, level.pxHei, color)
                    for (layer in (level.layerInstances ?: emptyList()).asReversed()) {
                        //for (layer in (level.layerInstances ?: emptyList())) {
                        val layerDef = layersDefsById[layer.layerDefUid] ?: continue
                        val tilesetExt = tilesetDefsById[layer.tilesetDefUid] ?: continue
                        val intGrid = IntArray2(layer.cWid, layer.cHei, layer.intGridCSV.copyOf(layer.cWid * layer.cHei))
                        val tileData = StackedIntArray2(layer.cWid, layer.cHei, -1)
                        val tileset = tilesetExt.def
                        val gridSize = tileset.tileGridSize

                        //val fsprites = FSprites(layer.autoLayerTiles.size)
                        //val view = fsprites.createView(bitmap).also { it.scale(2) }
                        //addChild(view)
                        for (tile in layer.autoLayerTiles) {
                            val (px, py) = tile.px
                            val (tileX, tileY) = tile.src
                            val x = px / gridSize
                            val y = py / gridSize
                            val dx = px % gridSize
                            val dy = py % gridSize
                            val tx = tileX / gridSize
                            val ty = tileY / gridSize
                            val cellsTilesPerRow = tileset.pxWid / gridSize
                            val tileId = ty * cellsTilesPerRow + tx
                            val flipX = tile.f.hasBitSet(0)
                            val flipY = tile.f.hasBitSet(1)
                            tileData.push(x, y, TileInfo(tileId, flipX = flipX, flipY = flipY, offsetX = dx, offsetY = dy).data)
                        }
                        if (tilesetExt.tileset != null) {
                            tileMap(tileData, tilesetExt.tileset).alpha(layerDef.displayOpacity)
                        }
                        //tileset!!.
                        //println(intGrid)
                    }
                }.xy(level.worldX, level.worldY)
                //break // ONLY FIRST LEVEL
                //}.filters(IdentityFilter.Nearest).scale(2)
            }
            //}.xy(300, 300)
        }
    }
}

class ExtTileset(val def: TilesetDefinition, val tileset: TileSet?)

class LDTKWorld(
    val ldtk: LDTKJson,
    val tilesetDefsById: Map<Int, ExtTileset>
) {
    val layersDefsById: Map<Int, LayerDefinition> = ldtk.defs.layers.associateBy { it.uid }
}

suspend fun VfsFile.readLDTKWorld(): LDTKWorld {
    val file = this
    val json = file.readString()
    val ldtk = LDTKJson.load(json)
    val tilesetDefsById: Map<Int, ExtTileset> = ldtk.defs.tilesets.associate { def ->
        val bitmap = def.relPath?.let { file.parent[it].readBitmap() }
        val tileSet = bitmap?.let { TileSet(bitmap.slice(), def.tileGridSize, def.tileGridSize) }
        def.uid to ExtTileset(def, tileSet)
    }
    return LDTKWorld(ldtk, tilesetDefsById)
}
