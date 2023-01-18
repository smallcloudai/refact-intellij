package com.smallcloud.codify.modes.diff.dialog.comboBox

import java.util.*
import javax.swing.DefaultComboBoxModel

class DefaultComboBoxModelReplace<E> : DefaultComboBoxModel<E> {
    private var objects: Vector<E?>? = null
    private var selectedObject: Any? = null

    constructor() {
        objects = Vector<E?>()
    }

    constructor(items: List<E?>) {
        objects = Vector<E?>(items.size)
        var i: Int
        val c: Int
        i = 0
        c = items.size
        while (i < c) {
            objects!!.addElement(items[i])
            i++
        }
        if (size > 0) {
            selectedObject = getElementAt(0)
        }
    }

    constructor(v: Vector<E?>?) {
        objects = v
        if (size > 0) {
            selectedObject = getElementAt(0)
        }
    }

    fun replaceElementAt(anObject: E?, index: Int) {
        objects!![index] = anObject
    }

    override fun setSelectedItem(anObject: Any?) {
        if (selectedObject != null && selectedObject != anObject ||
            selectedObject == null && anObject != null
        ) {
            selectedObject = anObject
            fireContentsChanged(this, -1, -1)
        }
    }

    override fun getSelectedItem(): Any? {
        return selectedObject
    }

    override fun getSize(): Int {
        return objects!!.size
    }

    override fun getElementAt(index: Int): E? {
        return if (index >= 0 && index < objects!!.size) objects!!.elementAt(index) else null
    }

    override fun getIndexOf(anObject: Any?): Int {
        return objects!!.indexOf(anObject)
    }

    override fun addElement(anObject: E?) {
        objects!!.addElement(anObject)
        fireIntervalAdded(this, objects!!.size - 1, objects!!.size - 1)
        if (objects!!.size == 1 && selectedObject == null && anObject != null) {
            selectedItem = anObject
        }
    }

    override fun insertElementAt(anObject: E?, index: Int) {
        objects!!.insertElementAt(anObject, index)
        fireIntervalAdded(this, index, index)
    }

    override fun removeElementAt(index: Int) {
        if (getElementAt(index) === selectedObject) {
            selectedItem = if (index == 0) {
                if (size == 1) null else getElementAt(index + 1)
            } else {
                getElementAt(index - 1)
            }
        }
        objects!!.removeElementAt(index)
        fireIntervalRemoved(this, index, index)
    }

    override fun removeElement(anObject: Any?) {
        val index = objects!!.indexOf(anObject)
        if (index != -1) {
            removeElementAt(index)
        }
    }

    override fun removeAllElements() {
        if (objects!!.size > 0) {
            val firstIndex = 0
            val lastIndex = objects!!.size - 1
            objects!!.removeAllElements()
            selectedObject = null
            fireIntervalRemoved(this, firstIndex, lastIndex)
        } else {
            selectedObject = null
        }
    }

    override fun addAll(c: Collection<E?>) {
        if (c.isEmpty()) {
            return
        }
        val startIndex = size
        objects!!.addAll(c)
        fireIntervalAdded(this, startIndex, size - 1)
    }

    override fun addAll(index: Int, c: Collection<E?>) {
        if (index < 0 || index > size) {
            throw ArrayIndexOutOfBoundsException(
                "index out of range: " +
                        index
            )
        }
        if (c.isEmpty()) {
            return
        }
        objects!!.addAll(index, c)
        fireIntervalAdded(this, index, index + c.size - 1)
    }
}