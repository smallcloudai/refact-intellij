package com.smallcloud.refact.modes.diff.dialog

import com.smallcloud.refact.struct.LongthinkFunctionEntry
import javax.swing.table.AbstractTableModel

class LongthinkTableModel(private val source: List<LongthinkFunctionEntry>,
                          private val fromHL: Boolean) : AbstractTableModel() {
    private lateinit var filtered: List<LongthinkFunctionEntry>

    init {
        filter("")
    }

    override fun getRowCount(): Int {
        return filtered.size
    }

    fun filter(filterStr: String) {
        filtered = filter(source, filterStr, fromHL)
        this.fireTableDataChanged()
    }

    override fun getColumnCount(): Int {
        return 3
    }

    fun isLikedChanged(entry: LongthinkFunctionEntry) {
        val idx = filtered.indexOf(entry)
        fireTableRowsUpdated(idx, idx)
    }

    fun elementAt(index: Int): LongthinkFunctionEntry {
        return filtered[index]
    }

    fun indexOf(entry: LongthinkFunctionEntry): Int {
        return filtered.indexOf(entry)
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val rec = elementAt(rowIndex)
        when (columnIndex) {
            0 -> return rec.label
//            1 -> return rec.metering
            1 -> return rec.isBookmarked
            else -> return Pair(rec.likes, rec.isLiked)
        }
    }

    override fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {}

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return false
    }
}