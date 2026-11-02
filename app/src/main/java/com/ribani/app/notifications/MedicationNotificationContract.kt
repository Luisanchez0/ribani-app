package com.ribani.app.notifications

object MedicationNotificationContract {
    const val ACTION_OPEN_MEDICATION = "com.ribani.app.OPEN_MEDICATION"
    const val EXTRA_MEDICATION_ID = "extra_medication_id"

    /** Acciones rápidas desde la alarma de toma (MedicationActionReceiver). */
    const val ACTION_MARK_TAKEN = "com.ribani.app.ACTION_MARK_TAKEN"
    const val ACTION_MARK_OMITTED = "com.ribani.app.ACTION_MARK_OMITTED"

    /** Alarma diaria que trae las tomas del día nuevo. */
    const val ACTION_RECARGAR_TOMAS = "com.ribani.app.ACTION_RECARGAR_TOMAS"

    /** Id de la toma (PATCH /tomas/{id}) que transportan alarma y acciones. */
    const val EXTRA_TOMA_ID = "extra_toma_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
}
