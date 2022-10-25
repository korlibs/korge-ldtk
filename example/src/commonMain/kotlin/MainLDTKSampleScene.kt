import com.soywiz.kds.*
import com.soywiz.klock.*
import com.soywiz.kmem.*
import com.soywiz.korge.input.*
import com.soywiz.korge.ldtk.*
import com.soywiz.korge.scene.*
import com.soywiz.korge.tween.*
import com.soywiz.korge.view.*
import com.soywiz.korge.view.filter.*
import com.soywiz.korge.view.tiles.*
import com.soywiz.korim.bitmap.*
import com.soywiz.korim.color.*
import com.soywiz.korim.format.*
import com.soywiz.korim.tiles.*
import com.soywiz.korim.tiles.TileSet
import com.soywiz.korio.file.std.*
import com.soywiz.korma.geom.*

abstract class PixelatedScene(
    sceneWidth: Int,
    sceneHeight: Int,
    sceneScaleMode: ScaleMode = ScaleMode.SHOW_ALL,
    sceneAnchor: Anchor = Anchor.CENTER,
    sceneSmoothing: Boolean = false,
) : ScaledScene(sceneWidth, sceneHeight, sceneScaleMode, sceneAnchor, sceneSmoothing = sceneSmoothing)

class MainLDTKSampleScene : PixelatedScene(1280, 720, sceneScaleMode = ScaleMode.NO_SCALE, sceneSmoothing = true) {
    override suspend fun SContainer.sceneMain() {
        onMagnify { println("magnify: ${it.amount}") }
        onRotate { println("rotate: ${it.amount}") }
        //onSwipe { println("swipe: ${it.amount}") }

        val json = resourcesVfs["ldtk/Typical_2D_platformer_example.ldtk"].readString()
        val ldtk = measureTime({
            LDTKJson.load(json)
        }) {
            println("Load LDTK [${json.length}] in $it")
        }
        class ExtTileset(val def: TilesetDefinition, val tileset: TileSet?)
        val layersDefsById = ldtk.defs.layers.associateBy { it.uid }
        val tilesetDefsById = ldtk.defs.tilesets.associate { def ->
            val bitmap = def.relPath?.let { resourcesVfs["ldtk/$it"].readBitmap() }
            val tileSet = bitmap?.let { TileSet(bitmap.slice(), def.tileGridSize, def.tileGridSize) }
            def.uid to ExtTileset(def, tileSet)
        }

        val container = container {
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
        }.filters(IdentityFilter.Linear).xy(300, 300)
        while (true) {
            tween(container::scale[0.5], time = 1.seconds)
            tween(container::scale[1.25], time = 1.seconds)
        }
    }
}
