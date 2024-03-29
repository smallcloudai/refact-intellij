package com.smallcloud.refactai.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyService.Companion.instance as PrivacyServiceInstance

@State(name = "com.smallcloud.refactai.settings.PrivacyState", storages = [
    Storage("CodifyPrivacy.xml", deprecated = true),
    Storage("SMCPrivacy.xml"),
])
class PrivacyState : PersistentStateComponent<PrivacyState> {
    @OptionTag
    var defaultPrivacy: Privacy = Privacy.ENABLED
    @OptionTag
    var dontAskDefaultPrivacyChanged: Boolean = false

    @OptionTag
    var privacyRecords: MutableList<PrivacyRecord> = emptyList<PrivacyRecord>().toMutableList()

    data class PrivacyRecord(
        @Attribute val filePath: String? = null,
        @Attribute var privacy: Privacy? = null
    ) : Comparable<PrivacyRecord> {
        override fun compareTo(other: PrivacyRecord): Int {
            return this.filePath!!.compareTo(other.filePath!!)
        }

    }

    override fun getState(): PrivacyState {
        return this
    }

//    private fun modifyPrivacyRecords(file: VirtualFile, privacy: Privacy?) {
//        val newRecord = PrivacyRecord(file.path, privacy)
//        if (privacyRecords.count { it.filePath == newRecord.filePath } != 0) {
//            privacyRecords.removeAll { it.filePath == newRecord.filePath }
//        }
//        privacyRecords.add(newRecord)
//    }


    fun restorePrivacyService() {
        PrivacyServiceInstance.clear()
        for (record in privacyRecords) {
            PrivacyServiceInstance.addMember(record.filePath!!, record.privacy)
        }
    }

    override fun loadState(state: PrivacyState) {
        XmlSerializerUtil.copyBean(state, this)
        restorePrivacyService()
    }

    companion object {
        @JvmStatic
        val instance: PrivacyState
            get() = ApplicationManager.getApplication().getService(PrivacyState::class.java)
    }
}