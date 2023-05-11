package com.smallcloud.refactai.aitoolbox.table

import com.smallcloud.refactai.aitoolbox.State
import com.smallcloud.refactai.struct.LongthinkFunctionVariation
import javax.swing.table.AbstractTableModel

class LongthinkTableModel(private var source: List<LongthinkFunctionVariation>,
                          private var fromHL_: Boolean) : AbstractTableModel() {
    private lateinit var filtered: List<LongthinkFunctionVariation>

    val currentData: List<LongthinkFunctionVariation>
        get() {
            return filtered
        }

    init {
        filter(State.currentIntent)
    }

    var data: List<LongthinkFunctionVariation>
        get() {
            return source
        }
        set(newData) {
            source = newData
        }

    var fromHL: Boolean = false
        set(newFromHL) {
            fromHL_ = newFromHL
        }


    override fun getRowCount(): Int {
        return filtered.size
    }

    fun filter(filterStr: String, filterBy: ((LongthinkFunctionVariation) -> Boolean)? = null) {
        filtered = com.smallcloud.refactai.aitoolbox.filter(source, filterStr, fromHL_, filterBy)
        this.fireTableDataChanged()
    }

    override fun getColumnCount(): Int {
        return 4
    }

    fun isLikedChanged(entry: LongthinkFunctionVariation) {
        val idx = filtered.indexOf(entry)
        fireTableRowsUpdated(idx, idx)
    }

    fun elementAt(index: Int): LongthinkFunctionVariation {
        return filtered[index]
    }

    fun indexOf(entry: LongthinkFunctionVariation): Int {
        return filtered.indexOf(entry)
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        if (rowIndex >= filtered.size) return null
        val rec = elementAt(rowIndex)
        return when (columnIndex) {
            0 -> rec.label
            1 -> rec.isBookmarked
            2 -> Pair(rec.likes, rec.isLiked)
            else -> true
        }
    }

    override fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {}

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == 3
    }
}