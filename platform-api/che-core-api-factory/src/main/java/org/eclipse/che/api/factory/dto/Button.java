/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.factory.dto;

import org.eclipse.che.api.core.factory.FactoryParameter;
import org.eclipse.che.dto.shared.DTO;

import static org.eclipse.che.api.core.factory.FactoryParameter.Obligation.OPTIONAL;

/**
 * Describes factory button
 *
 * @author Alexander Garagatyi
 */
@DTO
public interface Button {
    public enum ButtonType {
        logo, nologo
    }

    /** Type of the button */
    @FactoryParameter(obligation = OPTIONAL)
    ButtonType getType();

    void setType(ButtonType type);

    Button withType(ButtonType type);

    /** Button attributes */
    @FactoryParameter(obligation = OPTIONAL)
    ButtonAttributes getAttributes();

    void setAttributes(ButtonAttributes attributes);

    Button withAttributes(ButtonAttributes attributes);
}
