package com.smallcloud.codify.settings.renderer

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.privacy.PrivacyChangesNotifier
import com.smallcloud.codify.privacy.PrivacyService
import javax.swing.table.AbstractTableModel
import com.smallcloud.codify.settings.PrivacyState.Companion.instance as PrivacyStateInstance

private val stringToPrivacy = privacyToString.entries.map { Pair(it.value, it.key) }.toMap()

class PrivacyOverridesTableModel : AbstractTableModel() {
    override fun getRowCount(): Int {
        return PrivacyStateInstance.privacyRecords.size
    }

    override fun getColumnCount(): Int {
        return 3
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val rec = PrivacyStateInstance.privacyRecords.elementAt(rowIndex)
        when (columnIndex) {
            0 -> return rec.filePath
            1 -> return rec.privacy
            else -> return ""
        }
    }

    override fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 1) {
            val elem = PrivacyStateInstance.privacyRecords.elementAt(rowIndex)
            elem.privacy = stringToPrivacy[aValue as String]
            PrivacyService.instance.setPrivacy(elem.filePath!!, elem.privacy!!, true)
            fireTableCellUpdated(rowIndex, columnIndex)
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(PrivacyChangesNotifier.TOPIC)
                .privacyChanged()
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex > 0
    }
}