package com.example.ridetracker.util

import com.example.ridetracker.data.model.Ride
import com.example.ridetracker.data.model.RidePoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object GPXExporter {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(ride: Ride, points: List<RidePoint>, file: File) {
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"RideTracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append("  <trk>\n")
            append("    <name>Ride on ${isoFormat.format(Date(ride.startTime))}</name>\n")
            append("    <trkseg>\n")
            points.forEach { point ->
                append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
                append("        <ele>${point.altitude}</ele>\n")
                append("        <time>${isoFormat.format(Date(point.timestamp))}</time>\n")
                if (point.heartRate != null) {
                    append("        <extensions>\n")
                    append("          <gpxtpx:TrackPointExtension xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
                    append("            <gpxtpx:hr>${point.heartRate}</gpxtpx:hr>\n")
                    append("          </gpxtpx:TrackPointExtension>\n")
                    append("        </extensions>\n")
                }
                append("      </trkpt>\n")
            }
            append("    </trkseg>\n")
            append("  </trk>\n")
            append("</gpx>")
        }
        file.writeText(xml)
    }
}
