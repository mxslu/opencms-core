/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui.dialogs.availability;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.lock.CmsLockActionRecord;
import org.opencms.lock.CmsLockActionRecord.LockChange;
import org.opencms.lock.CmsLockException;
import org.opencms.lock.CmsLockUtil;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.ui.A_CmsUI;
import org.opencms.ui.CmsVaadinUtils;
import org.opencms.ui.I_CmsDialogContext;
import org.opencms.ui.components.CmsBasicDialog;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.DateField;

/**
 * Availability dialog.<p>
 */
public class CmsAvailabilityDialog extends CmsBasicDialog {

    private static final long serialVersionUID = 1L;

    /** Logger for this class. */
    private static final Log LOG = CmsLog.getLog(CmsAvailabilityDialog.class);

    /** The cancel button. */
    private Button m_cancelButton;

    /** The dialog context. */
    private I_CmsDialogContext m_dialogContext;

    /** Date field. */
    private DateField m_expiredField;

    /** OK button. */
    private Button m_okButton;

    /** Date field. */
    private DateField m_releasedField;

    /** Option to reset the expiration. */
    private CheckBox m_resetExpired;

    /** Option to reset the relase date. */
    private CheckBox m_resetReleased;

    /** Option to enable subresource modification. */
    private CheckBox m_subresourceModificationField;

    /**
     * Creates a new instance.<p>
     *
     * @param dialogContext the dialog context
     */
    public CmsAvailabilityDialog(I_CmsDialogContext dialogContext) {

        super();
        m_dialogContext = dialogContext;
        CmsVaadinUtils.readAndLocalizeDesign(
            this,
            OpenCms.getWorkplaceManager().getMessages(A_CmsUI.get().getLocale()),
            null);

        List<CmsResource> resources = dialogContext.getResources();
        if (resources.size() == 1) {
            CmsResource onlyResource = resources.get(0);
            if (onlyResource.getDateReleased() != CmsResource.DATE_RELEASED_DEFAULT) {
                m_releasedField.setValue(new Date(onlyResource.getDateReleased()));
            }
            if (onlyResource.getDateExpired() != CmsResource.DATE_EXPIRED_DEFAULT) {
                m_expiredField.setValue(new Date(onlyResource.getDateExpired()));
            }
        }
        boolean hasFolders = false;
        for (CmsResource resource : resources) {
            if (resource.isFolder()) {
                hasFolders = true;
            }
        }
        m_subresourceModificationField.setVisible(hasFolders);
        initResetCheckbox(m_resetReleased, m_releasedField);
        initResetCheckbox(m_resetExpired, m_expiredField);
        m_okButton.addClickListener(new ClickListener() {

            private static final long serialVersionUID = 1L;

            @SuppressWarnings("synthetic-access")
            public void buttonClick(ClickEvent event) {

                if (validate()) {
                    try {

                        changeAvailability();
                        m_dialogContext.finish(null);
                    } catch (Throwable t) {
                        m_dialogContext.error(t);
                    }
                }
            }

        });

        m_cancelButton.addClickListener(new ClickListener() {

            private static final long serialVersionUID = 1L;

            @SuppressWarnings("synthetic-access")
            public void buttonClick(ClickEvent event) {

                m_dialogContext.finish(null);
            }
        });
        displayResourceInfo(m_dialogContext.getResources());
    }

    /**
     * Actually performs the availability change.<p>
     *
     * @throws CmsException if something goes wrong
     */
    protected void changeAvailability() throws CmsException {

        Date released = m_releasedField.getValue();
        Date expired = m_expiredField.getValue();
        boolean resetReleased = m_resetReleased.getValue().booleanValue();
        boolean resetExpired = m_resetExpired.getValue().booleanValue();
        boolean modifySubresources = m_subresourceModificationField.getValue().booleanValue();
        for (CmsResource resource : m_dialogContext.getResources()) {
            changeAvailability(resource, released, resetReleased, expired, resetExpired, modifySubresources);
        }

    }

    /**
     * Changes availability.<p>
     *
     * @param resource the resource
     * @param released release date
     * @param resetReleased reset release date
     * @param expired expiration date
     * @param resetExpired reset expiration date
     * @param modifySubresources modify children
     *
     * @throws CmsException if somthing goes wrong
     */
    private void changeAvailability(
        CmsResource resource,
        Date released,
        boolean resetReleased,
        Date expired,
        boolean resetExpired,
        boolean modifySubresources) throws CmsException {

        CmsObject cms = m_dialogContext.getCms();
        CmsLockActionRecord lockActionRecord = CmsLockUtil.ensureLock(cms, resource);
        try {
            long newDateReleased;
            if (resetReleased || (released != null)) {
                newDateReleased = released != null ? released.getTime() : CmsResource.DATE_RELEASED_DEFAULT;
                cms.setDateReleased(resource, newDateReleased, modifySubresources);
            }
            long newDateExpired;
            if (resetExpired || (expired != null)) {
                newDateExpired = expired != null ? expired.getTime() : CmsResource.DATE_EXPIRED_DEFAULT;
                cms.setDateExpired(resource, newDateExpired, modifySubresources);
            }
        } finally {
            if (lockActionRecord.getChange() == LockChange.locked) {
                try {
                    cms.unlockResource(resource);
                } catch (CmsLockException e) {
                    LOG.warn(e.getLocalizedMessage(), e);
                }
            }

        }
    }

    /**
     * Creates a reset checkbox which can enable / disable a date field.<p>
     *
     * @param box the check box
     * @param field the date field
     */
    private void initResetCheckbox(CheckBox box, final DateField field) {

        box.addValueChangeListener(new ValueChangeListener() {

            private static final long serialVersionUID = 1L;

            public void valueChange(ValueChangeEvent event) {

                Boolean value = (Boolean)(event.getProperty().getValue());
                if (value.booleanValue()) {
                    field.clear();
                    field.setEnabled(false);
                } else {
                    field.setEnabled(true);
                }
            }
        });
    }

    /**
     * Validates release / expiration.<p>
     *
     * @return true if the fields are valid.
     */
    private boolean validate() {

        return m_releasedField.isValid() && m_expiredField.isValid();
    }
}