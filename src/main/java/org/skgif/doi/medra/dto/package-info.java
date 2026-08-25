/**
 * Record types for mEDRA's parsed ONIX-for-DOI metadata.
 */
@SuppressFBWarnings(value = {EI_EXPOSE_REP, EI_EXPOSE_REP2}, justification = "Plain JSON-deserialized data " +
        "carriers with no independent mutation path once deserialized - " + SPOTBUGS_REGISTER)
package org.skgif.doi.medra.dto;

import static org.skgif.doi.util.SpotBugsError.Code.EI_EXPOSE_REP;
import static org.skgif.doi.util.SpotBugsError.Code.EI_EXPOSE_REP2;
import static org.skgif.doi.util.SpotBugsError.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
