package app.monitor.feature.dashboard.components

import app.monitor.core.model.CoolingSnapshot
import app.monitor.core.model.SystemSnapshot

fun resolveCoolingSnapshot(snapshot: SystemSnapshot?): CoolingSnapshot? = snapshot?.cooling
