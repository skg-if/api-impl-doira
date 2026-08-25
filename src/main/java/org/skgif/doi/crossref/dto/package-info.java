/**
 * Jackson DTOs for Crossref's REST API JSON responses.
 */
@SuppressFBWarnings(value = {EI_EXPOSE_REP, EI_EXPOSE_REP2}, justification = "Plain JSON-deserialized data " +
        "carriers with no independent mutation path once deserialized - " + SPOTBUGS_REGISTER)
package org.skgif.doi.crossref.dto;

import static org.skgif.doi.util.SpotBugsSuppressions.EI_EXPOSE_REP;
import static org.skgif.doi.util.SpotBugsSuppressions.EI_EXPOSE_REP2;
import static org.skgif.doi.util.SpotBugsSuppressions.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
