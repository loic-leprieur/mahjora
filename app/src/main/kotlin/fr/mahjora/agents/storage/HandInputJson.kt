package fr.mahjora.agents.storage

import fr.mahjora.core.Circumstance
import fr.mahjora.core.HandInput
import fr.mahjora.core.Meld
import fr.mahjora.core.MeldKind
import fr.mahjora.core.Tile
import fr.mahjora.core.WinMethod
import fr.mahjora.core.Wind
import org.json.JSONArray
import org.json.JSONObject

/** Stable JSON form of a [HandInput], stored with each hand so it can be reopened and re-scored. */
object HandInputJson {
    fun encode(h: HandInput): String = JSONObject().apply {
        put("concealed", JSONArray(h.concealed.map { it.code }))
        put("declared", JSONArray(h.declared.map { m ->
            JSONObject()
                .put("kind", m.kind.name)
                .put("tiles", JSONArray(m.tiles.map { it.code }))
                .put("concealed", m.concealed)
        }))
        put("flowers", h.flowers)
        put("win", h.winningTile.code)
        put("method", h.winMethod.name)
        put("circumstances", JSONArray(h.circumstances.map { it.name }))
        put("seat", h.seatWind.name)
        put("round", h.roundWind.name)
    }.toString()

    fun decode(json: String): HandInput {
        val o = JSONObject(json)
        fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }
        val declared = o.getJSONArray("declared").let { a ->
            List(a.length()) {
                val m = a.getJSONObject(it)
                Meld(
                    MeldKind.valueOf(m.getString("kind")),
                    strings(m.getJSONArray("tiles")).map(Tile::parse),
                    m.getBoolean("concealed"),
                )
            }
        }
        return HandInput(
            concealed = strings(o.getJSONArray("concealed")).map(Tile::parse),
            declared = declared,
            flowers = o.getInt("flowers"),
            winningTile = Tile.parse(o.getString("win")),
            winMethod = WinMethod.valueOf(o.getString("method")),
            circumstances = strings(o.getJSONArray("circumstances")).map(Circumstance::valueOf).toSet(),
            seatWind = Wind.valueOf(o.getString("seat")),
            roundWind = Wind.valueOf(o.getString("round")),
        )
    }
}
