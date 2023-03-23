package com.smallcloud.refactai.privacy

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.smallcloud.refactai.settings.PrivacyState
import java.nio.file.Path

enum class Privacy {
    DISABLED,
    ENABLED,
    THIRDPARTY
}

data class PrivacyMember(
    var name: String,
    var privacy: Privacy? = null,
    var isDir: Boolean = true,
    var children: MutableMap<String, PrivacyMember>? = null,
    var parent: PrivacyMember? = null
) {
    private fun mergeNodes(newNode: PrivacyMember): PrivacyMember {
        if (newNode.privacy != null)
            privacy = newNode.privacy
        if (children == null) {
            children = newNode.children
        } else if (newNode.children != null) {
            children = (children!! + newNode.children!!).toMutableMap()
        }
        return this
    }

    fun addToChildren(newNode: PrivacyMember): PrivacyMember {
        newNode.parent = this
        if (children == null) {
            children = mutableMapOf()
        }
        val child = children?.get(newNode.name)
        if (child != null) {
            children?.set(newNode.name, child.mergeNodes(newNode))
        } else {
            children?.set(newNode.name, newNode)
        }
        return children?.get(newNode.name)!!
    }
    fun getFullPath(): String {
        val path = emptyList<String>().toMutableList()
        var node: PrivacyMember? = this
        while (node != null) {
            path.add(0, node.name)
            node = node.parent
        }
        val realPath = Path.of(path[0], *path.subList(1, path.size).toTypedArray())
        return realPath.toString()
    }

    fun getParentWithPrivacy() : PrivacyMember? {
        var localParent = parent
        while (localParent != null) {
            if (localParent.privacy != null) return localParent
            localParent = localParent.parent
        }
        return null
    }

    override fun toString(): String {
        return name
    }
}


class PrivacyService : VetoableProjectManagerListener, Disposable {
    private var privacyTree: MutableMap<String, PrivacyMember> = emptyMap<String, PrivacyMember>().toMutableMap()

    init {
        ProjectManager.getInstance().addProjectManagerListener(this)
    }

    override fun projectOpened(project: Project) {
        val path = project.basePath
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path))
        val node = virtualFile?.let { getMember(it) }
        if (virtualFile != null && (node?.privacy == null)) {
            setPrivacy(virtualFile, PrivacyState.instance.defaultPrivacy, true)
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(PrivacyChangesNotifier.TOPIC)
                .privacyChanged()
        }
    }

    fun getAllNestedChildren(file: VirtualFile) : List<PrivacyMember> {
        val node = getMember(file) ?: return emptyList()
        val res = emptyList<PrivacyMember>().toMutableList()
        fun getAllNestedChildrenImpl(node: PrivacyMember) {
            if (node.privacy != null) res.add(node)
            if (node.children == null) return
            node.children?.forEach {
                getAllNestedChildrenImpl(it.value)
            }
        }
        if (node.children != null) {
            node.children!!.forEach { getAllNestedChildrenImpl(it.value) }
        }
        return res.toList()
    }

    fun setPrivacy(file: String, privacy: Privacy, only: Boolean = false) {
        VirtualFileManager.getInstance().findFileByNioPath(Path.of(file))?.let { setPrivacy(it, privacy, only) }
    }

    fun setPrivacy(file: VirtualFile, privacy: Privacy, only: Boolean = false) {
        if (!file.isInLocalFileSystem) return
        var node = getMember(file)
        if (node == null)
            node = addMember(file)
        node?.privacy = privacy
        if (file.isDirectory && !only)
            node?.children = null
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(PrivacyChangesNotifier.TOPIC)
            .privacyChanged()

        PrivacyState.instance.privacyRecords = getPrivacyRecords().toSet().toMutableList()
    }

    private fun getPrivacyFromParents(node: PrivacyMember): Privacy {
        var node: PrivacyMember? = node
        while (node != null) {
            if (node.privacy != null)
                return node.privacy!!
            node = node.parent
        }
        return PrivacyState.instance.defaultPrivacy
    }

    fun getPrivacy(file: String): Privacy {
        return VirtualFileManager.getInstance().findFileByNioPath(Path.of(file))?.let {
            getPrivacy(it)
        }!!
    }

    fun getPrivacy(file: VirtualFile?): Privacy {
        if (file == null) return Privacy.DISABLED
        val node = getMember(file, true) ?: return PrivacyState.instance.defaultPrivacy

        if (node.privacy == null) return getPrivacyFromParents(node)
        return node.privacy!!
    }

    fun getMember(file: String, getParentIfFileNotExists: Boolean = false): PrivacyMember? {
        return VirtualFileManager.getInstance().findFileByNioPath(Path.of(file))?.let {
            getMember(it, getParentIfFileNotExists)
        }
    }

    fun getMember(file: VirtualFile, getParentIfFileNotExists: Boolean = false): PrivacyMember? {
        if (!file.isInLocalFileSystem) return null
        val path = getSplitPath(file)
        var parent = privacyTree[path[0]]
        if (parent == null) return null

        for (member in path.subList(1, path.size)) {
            if (parent!!.children?.contains(member) == true) {
                parent = parent.children?.get(member)
            } else if (getParentIfFileNotExists) return parent else return null
        }
        return parent
    }

    private fun getSplitPath(file: VirtualFile): List<String> {
        val path: MutableList<String> = mutableListOf(file.name)
        var parent: VirtualFile? = file
        while (true) {
            parent = parent?.parent
            if (parent == null) break
            path.add(0, parent.name)
        }
        return path
    }

    fun addMember(file: String, privacy: Privacy? = null): PrivacyMember? {
        return VirtualFileManager.getInstance().findFileByNioPath(Path.of(file))?.let { addMember(it, privacy) }
    }

    private fun addMember(file: VirtualFile, privacy: Privacy? = null): PrivacyMember? {
        val path = getSplitPath(file)

        if (privacyTree[path[0]] == null) {
            privacyTree[path[0]] = PrivacyMember(path[0], isDir = true)
        }

        var treeParentNode: PrivacyMember? = privacyTree[path[0]]

        path.withIndex().forEach {
            if (it.index == path.size - 1) {
                treeParentNode = treeParentNode?.addToChildren(PrivacyMember(it.value, privacy, isDir = false))
            } else if (it.index == 0) {
                // skip
            } else {
                treeParentNode = treeParentNode?.addToChildren(PrivacyMember(it.value, isDir = true))
            }
        }
        return treeParentNode
    }

    fun getPrivacyRecords(): List<PrivacyState.PrivacyRecord> {
        val result = emptyList<PrivacyState.PrivacyRecord>().toMutableList()
        fun findAndAdd(node: PrivacyMember) {
            if (node.privacy != null) {
                result.add(PrivacyState.PrivacyRecord(node.getFullPath(), node.privacy!!))
            }
            node.children?.forEach { findAndAdd(it.value) }
        }
        privacyTree.forEach { findAndAdd(it.value) }
        return result
    }

    fun clear() {
        privacyTree.clear()
    }

    companion object {
        @JvmStatic
        val instance: PrivacyService
            get() = ApplicationManager.getApplication().getService(PrivacyService::class.java)
    }

    override fun canClose(project: Project): Boolean {
        return true
    }

    override fun dispose() {
        ProjectManager.getInstance().removeProjectManagerListener(this)
    }
}