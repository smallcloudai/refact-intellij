package com.smallcloud.codify.modes.diff.dialog

import com.smallcloud.codify.Resources.stagingFilterPrefix
import com.smallcloud.codify.struct.LongthinkFunctionEntry
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

    private fun filterByString(filter: String?): List<LongthinkFunctionEntry> {
        var realFilter = filter!!.lowercase()
        while (realFilter.startsWith(" ")) {
            realFilter = realFilter.substring(1)
        }
        val realStagingFilter = stagingFilterPrefix.lowercase() + " " + realFilter
        return source.filter {
            it.label.lowercase().startsWith(realFilter) ||
                    it.label.lowercase().startsWith(realStagingFilter) ||
                    it.catchAny()
        }

    }

    fun filter(filterStr: String?) {
        val localFiltered = filterByString(filterStr).toMutableList()
        filtered = if (fromHL) {
            localFiltered.sortedWith(compareByDescending<LongthinkFunctionEntry> { it.isBookmarked }
                .thenByDescending { it.catchAllHighlight }
                .thenByDescending { it.catchAllSelection }
                .thenByDescending { it.catchQuestionMark }
                .thenByDescending { it.likes }
                .thenByDescending { it.supportHighlight }
            )
        } else {
            localFiltered.sortedWith(compareByDescending<LongthinkFunctionEntry> { it.isBookmarked }
                .thenByDescending { it.catchAllSelection }
                .thenByDescending { it.catchAllHighlight }
                .thenByDescending { it.catchQuestionMark }
                .thenByDescending { it.likes }
                .thenByDescending { it.supportHighlight }
            )
        }
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