package com.smallcloud.codify.panes.gptchat.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent


val FIELD_INPLACE_LOOK: ActionButtonLook = object : IdeaActionButtonLook() {
    private val BUTTON_SELECTED_BACKGROUND = JBColor.namedColor("SearchOption.selectedBackground", 0xDAE4ED, 0x5C6164)
    override fun paintBorder(g: Graphics, component: JComponent, @ActionButtonComponent.ButtonState state: Int) {
        if (component.isFocusOwner && component.isEnabled) {
            val rect = Rectangle(component.size)
            JBInsets.removeFrom(rect, component.insets)
            SYSTEM_LOOK.paintLookBorder(g, rect, JBUI.CurrentTheme.ActionButton.focusedBorder())
        } else {
            super.paintBorder(g, component, ActionButtonComponent.NORMAL)
        }
    }

    override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
        if ((component as MyActionButton).isRolloverState) {
            super.paintBackground(g, component, state)
        } else if (state == ActionButtonComponent.SELECTED && component.isEnabled()) {
            val rect = Rectangle(component.getSize())
            JBInsets.removeFrom(rect, component.getInsets())
            paintLookBackground(g, rect, BUTTON_SELECTED_BACKGROUND)
        }
    }
}

class MyActionButton(action: AnAction, focusable: Boolean, fieldInplaceLook: Boolean) :
        ActionButton(action, action.templatePresentation.clone(),
                ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
    init {
        setLook(if (fieldInplaceLook) FIELD_INPLACE_LOOK else ActionButtonLook.INPLACE_LOOK)
        isFocusable = focusable
        updateIcon()
    }

    override fun getDataContext(): DataContext {
        return DataManager.getInstance().getDataContext(this)
    }

    override fun getPopState(): Int {
        return if (isSelected) SELECTED else super.getPopState()
    }

    val isRolloverState: Boolean
        get() = super.isRollover()

    override fun getIcon(): Icon {
        if (isEnabled && isSelected) {
            val selectedIcon = myPresentation.selectedIcon
            if (selectedIcon != null) return selectedIcon
        }
        return super.getIcon()
    }
}
