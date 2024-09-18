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
import korlibs.korge.view.filter.IdentityFilter
import korlibs.korge.view.filter.filters
import korlibs.math.geom.*
import korlibs.math.geom.slice.*


private fun IStackedIntArray2.getFirst(pos: PointInt): Int = getFirst(pos.x, pos.y)
private fun IStackedIntArray2.getLast(pos: PointInt): Int = getLast(pos.x, pos.y)

class LDTKCollisions(val world: LDTKWorld, val stack: IStackedIntArray2) {
    fun tileToPixel(tilePos: PointInt): PointInt = (tilePos.toDouble() * world.ldtk.defaultGridSize).toIntFloor()
    fun pixelToTile(pixelPos: PointInt): PointInt = (pixelPos.toDouble() / world.ldtk.defaultGridSize).toIntFloor()

    fun getTile(tilePos: PointInt): Int = stack.getLast(tilePos)
    fun getPixel(pixelPos: PointInt): Int = getTile(pixelToTile(pixelPos))
    fun getPixel(pixelPos: Point): Int = getPixel(pixelPos.toIntFloor())
}

fun LDTKWorld.createCollisionMaps(layerId: String = "Collisions"): LDTKCollisions {
    val ldtk = this.ldtk
    val world = SparseChunkedStackedIntArray2()
    for (level in ldtk.levels) {
        //println("## level: ${level.identifier}")
        for (layer in (level.layerInstances ?: emptyList()).asReversed()) {
            if (layer.identifier != layerId) continue
            val intGrid = IntArray2(layer.cWid, layer.cHei, layer.intGridCSV.copyOf(layer.cWid * layer.cHei))
            //println("intGrid=$intGrid")
            //println(" - layer=${layer.identifier}, level.worldX=${level.worldX}, level.worldY=${level.worldY}")
            world.putChunk(
                StackedIntArray2(
                    intGrid,
                    startX = level.worldX / ldtk.defaultGridSize,
                    startY = level.worldY / ldtk.defaultGridSize
                )
            )
        }
    }
    return LDTKCollisions(this, world)
}

class LDTKEntityView(
    val entity: EntityInstance,
    val llayer: LDTKLayer
) : Container() {

    val definition = llayer.world.ldtk.defs.entitiesByUid[entity.defUid]!!
    val fields = definition.fieldDefs.zip(entity.fieldInstances).map { FieldInfo(it.first, it.second) }
    val fieldsByName = fields.associateBy { it.identifier }

    init {
        llayer.level.level.fieldInstances
        name = entity.identifier
    }

    val anchor = entity.pivotAnchor
    val gridSize = llayer.layer.gridSize.toDouble()
    val tile: TilesetRectangle? = entity.tile
    val tileset = llayer.world.tilesetDefsById[tile?.tilesetUid]
    val utileset = tileset?.unextrudedTileSet
    var view: View = if (tile != null) {
        image(tileset!!.unextrudedTileSet!!.base.sliceWithSize(tile.x, tile.y, tile.w, tile.h)).also { it.smoothing = false }
    } else {
        solidRect(entity.width, entity.height, Colors[entity.smartColor, Colors.FUCHSIA])
    }
    fun replaceView(view: View) {
        this.replaceChild(this.view, view)
        this.view = view
        //view.anchor(anchor)
    }
    init {
        val pos = entity.gridPos.toDouble() * gridSize + anchor.toVector() * gridSize
        view
            .size(entity.width, entity.height)
        (view as? Anchorable)?.anchor(anchor)

        this.xy(pos)
        this.zIndex(pos.y)
    }

}

// Bring back extention function from Korge 5
inline fun Container.tileMap(
    map: TileMapData,
    tileSet: TileSet,
    smoothing: Boolean = true,
    callback: @ViewDslMarker TileMap.() -> Unit = {},
) = TileMap(map, tileSet, smoothing, tileSet.tileSize).repeat(map.repeatX, map.repeatY).addTo(this, callback)

class LDTKLayerView(
    val llayer: LDTKLayer,
    var showCollisions: Boolean = false
) : Container() {
    val world = llayer.world
    val layer = llayer.layer

    val layerDef = world.layersDefsById[layer.layerDefUid]
    val tilesetExt = world.tilesetDefsById[layer.tilesetDefUid]

    val intGrid = TileMapData(width = layer.cWid, height = layer.cHei)
    val tileData = TileMapData(width = layer.cWid, height = layer.cHei)

    fun addTiles() {
        if (tilesetExt != null && layerDef != null) {
            val tileset = tilesetExt.def
            val gridSize = tileset.tileGridSize

            //val fsprites = FSprites(layer.autoLayerTiles.size)
            //val view = fsprites.createView(bitmap).also { it.scale(2) }
            //addChild(view)

            for (tile in layer.autoLayerTiles + layer.gridTiles) {
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
                tileData.push(x, y, Tile(tile = tileId, offsetX = dx, offsetY = dy, flipX = flipX, flipY = flipY, rotate = false))
            }
            if (tilesetExt.tileset != null) {
                 tileMap(tileData, tilesetExt.tileset, smoothing = false)
                    .alpha(layerDef.displayOpacity)
                    .also { if (!world.tilesetIsExtruded) it.filters(IdentityFilter.Nearest) }
                    .also { it.overdrawTiles = 1 }
                tileMap(intGrid, world.intsTileSet, smoothing = false)
                    .visible(showCollisions)
                    .also { if (!world.tilesetIsExtruded) it.filters(IdentityFilter.Nearest) }
                    .also { it.overdrawTiles = 1 }
            }
            //tileset!!.
            //println(intGrid)
        }
    }

    init {
        name = layer.identifier
        //for (layer in (level.layerInstances ?: emptyList())) {
        addTiles()
    }

    val entities = layer.entityInstances.map { LDTKEntityView(it, llayer).addTo(this) }
    val entitiesByIID = entities.associateBy { it.entity.iid }
}

class LDTKLevelView(
    val level: LDTKLevel,
    private var showCollisions: Boolean = false,
    private var showBackground: Boolean = true
) : Container() {
    private val ldtk get() = level.ldtk
    private val world get() = level.world
    private val blevel get() = level.level

    private val _layerViews = arrayListOf<View>()
    private val _layerViewsByName = linkedHashMapOf<String, View>()

    val bgLayer = solidRect(blevel.pxWid, blevel.pxHei, Colors[blevel.levelBgColor ?: ldtk.defaultLevelBgColor]).also {
        it.name = "background"
        it.visible = showBackground
    }
    val layerViews = level.layers.asReversed().map { layer -> LDTKLayerView(layer, showCollisions).addTo(this) }
    val layerViewsByName = layerViews.associateBy { it.layer.identifier }
}

class LDTKWorldView(
    val world: LDTKWorld,
    showCollisions: Boolean = false,
    showBackground: Boolean = true
) : Container() {
    init {
        for (level in world.levels) {
            LDTKLevelView(level, showCollisions, showBackground)
                .addTo(this)
                .xy(level.level.worldX, level.level.worldY)
        }
    }
}

class ExtTileset(val def: TilesetDefinition, val tileset: TileSet?, val unextrudedTileSet: TileSet?)

class LDTKLayer(val level: LDTKLevel, val layer: LayerInstance) {
    val world get() = level.world
    val entities get() = layer.entityInstances
}

class LDTKLevel(val world: LDTKWorld, val level: Level) {
    val ldtk get() = world.ldtk
    val layers by lazy { level.layerInstances?.map { LDTKLayer(this, it) } ?: emptyList() }
    val layersByName by lazy { layers.associateBy { it.layer.identifier } }
}

class LDTKWorld(
    val ldtk: LDTKJson,
    val tilesetDefsById: Map<Int, ExtTileset>,
    val tilesetIsExtruded: Boolean
) {
    val levels by lazy { ldtk.levels.map { LDTKLevel(this, it) } }
    val levelsByName by lazy { levels.associateBy { it.level.identifier } }

    val layersDefsById: Map<Int, LayerDefinition> = ldtk.defs.layers.associateBy { it.uid }

    //val ldtk = world.ldtk
    //val layersDefsById = world.layersDefsById
    //val tilesetDefsById = world.tilesetDefsById

    val colors = Bitmap32((ldtk.defaultGridSize + 4) * 16, ldtk.defaultGridSize)
    val intsTileSet = TileSet(
        (0 until 16).map {
            TileSetTileInfo(
                it,
                colors.slice(
                    RectangleInt(
                        (ldtk.defaultGridSize + 4) * it,
                        0,
                        ldtk.defaultGridSize,
                        ldtk.defaultGridSize
                    )
                )
            )
        }
    )

    init {
        // @TODO: Do this for each layer, since we might have several IntGrid layers
        for (layer in ldtk.defs.layers) {
            for (value in layer.intGridValues) {
                colors.fill(Colors[value.color], (ldtk.defaultGridSize + 4) * value.value)
                //println("COLOR: ${value.value} : ${value.color}")
            }
        }
    }
}

suspend fun VfsFile.readLDTKWorld(extrude: Boolean = true): LDTKWorld {
    val file = this
    val json = file.readString()
    val ldtk = LDTKJson.load(json)
    val tilesetDefsById: Map<Int, ExtTileset> = ldtk.defs.tilesets.associate { def ->
        val bitmap = def.relPath?.let {
            val bmp = file.parent[it].readBitmap()
            if (extrude) bmp.toBMP32() else bmp
        }
        val unextrudedTileSet: TileSet? = bitmap?.let {
            TileSet(bitmap.slice(), def.tileGridSize, def.tileGridSize)
        }
        val tileSet: TileSet? = unextrudedTileSet?.let {
            if (extrude) unextrudedTileSet.extrude(border = 2) else unextrudedTileSet
        }
        def.uid to ExtTileset(def, tileSet, unextrudedTileSet)
    }
    return LDTKWorld(ldtk, tilesetDefsById, extrude)
}

fun TileSet.extrude(border: Int = 1, mipmaps: Boolean = false): TileSet {
    val bitmaps = this.textures.map { (it as BmpSlice).extract().toBMP32() }
    return TileSet.fromBitmaps(width, height, bitmaps, border, mipmaps = mipmaps)
}
