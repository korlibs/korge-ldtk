import korlibs.datastructure.IntArray2
import korlibs.datastructure.StackedIntArray2
import korlibs.image.bitmap.slice
import korlibs.image.color.Colors
import korlibs.image.format.readBitmap
import korlibs.image.tiles.TileSet
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.onMagnify
import korlibs.korge.input.onRotate
import korlibs.korge.ldtk.LDTKJson
import korlibs.korge.ldtk.TilesetDefinition
import korlibs.korge.scene.ScaledScene
import korlibs.korge.tween.get
import korlibs.korge.tween.tween
import korlibs.korge.view.*
import korlibs.korge.view.filter.IdentityFilter
import korlibs.korge.view.filter.filters
import korlibs.korge.view.tiles.TileInfo
import korlibs.korge.view.tiles.tileMap
import korlibs.math.geom.Anchor
import korlibs.math.geom.ScaleMode
import korlibs.math.geom.SizeInt
import korlibs.memory.hasBitSet
import korlibs.time.measureTime
import korlibs.time.seconds

abstract class PixelatedScene(
    sceneSize: SizeInt,
    sceneScaleMode: ScaleMode = ScaleMode.SHOW_ALL,
    sceneAnchor: Anchor = Anchor.CENTER,
    sceneSmoothing: Boolean = false,
) : ScaledScene(sceneSize.width, sceneSize.height, sceneScaleMode, sceneAnchor, sceneSmoothing = sceneSmoothing)

class MainLDTKSampleScene : PixelatedScene(SizeInt(1280, 720), sceneScaleMode = ScaleMode.NO_SCALE, sceneSmoothing = true) {
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
            tween(container::scaleAvg[0.5], time = 1.seconds)
            tween(container::scaleAvg[1.25], time = 1.seconds)
        }
    }
}
