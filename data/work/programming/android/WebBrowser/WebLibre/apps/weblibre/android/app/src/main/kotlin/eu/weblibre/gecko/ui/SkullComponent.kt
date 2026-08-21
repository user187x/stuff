/*
 * Copyright (c) 2024-2025 Fabian Freund.
 *
 * This file is part of WebLibre
 * (see https://weblibre.eu).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package eu.weblibre.gecko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SkullComponent(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    Canvas(modifier = modifier.size(300.dp)) {
        val width = size.width
        val height = size.height

        val mainPath = Path().apply {
            // Cranium (Round dome)
            moveTo(width * 0.5f, height * 0.05f)
            cubicTo(width * 0.15f, height * 0.05f, width * 0.15f, height * 0.45f, width * 0.15f, height * 0.55f)
            
            // Temple indent
            lineTo(width * 0.22f, height * 0.57f)
            lineTo(width * 0.20f, height * 0.65f)
            
            // Left cheek/Jaw
            cubicTo(width * 0.20f, height * 0.85f, width * 0.35f, height * 0.95f, width * 0.42f, height * 0.95f)
            
            // Teeth bumps
            cubicTo(width * 0.43f, height * 0.92f, width * 0.45f, height * 0.92f, width * 0.46f, height * 0.95f)
            cubicTo(width * 0.47f, height * 0.92f, width * 0.49f, height * 0.92f, width * 0.50f, height * 0.95f)
            cubicTo(width * 0.51f, height * 0.92f, width * 0.53f, height * 0.92f, width * 0.54f, height * 0.95f)
            cubicTo(width * 0.55f, height * 0.92f, width * 0.57f, height * 0.92f, width * 0.58f, height * 0.95f)

            // Right cheek/Jaw
            cubicTo(width * 0.65f, height * 0.95f, width * 0.80f, height * 0.85f, width * 0.80f, height * 0.65f)
            
            // Temple indent (right side)
            lineTo(width * 0.78f, height * 0.57f)
            lineTo(width * 0.85f, height * 0.55f)
            
            // Right side dome
            cubicTo(width * 0.85f, height * 0.45f, width * 0.85f, height * 0.05f, width * 0.5f, height * 0.05f)
            
            close()
        }

        drawPath(path = mainPath, color = color, style = Fill)

        // Draw Eye Sockets and Nasal Cavity in white
        val whitePartsPath = Path().apply {
            // Left Eye
            moveTo(width * 0.28f, height * 0.58f)
            cubicTo(width * 0.28f, height * 0.50f, width * 0.48f, height * 0.50f, width * 0.48f, height * 0.58f)
            cubicTo(width * 0.48f, height * 0.70f, width * 0.28f, height * 0.70f, width * 0.28f, height * 0.58f)
            close()

            // Right Eye
            moveTo(width * 0.52f, height * 0.58f)
            cubicTo(width * 0.52f, height * 0.50f, width * 0.72f, height * 0.50f, width * 0.72f, height * 0.58f)
            cubicTo(width * 0.72f, height * 0.70f, width * 0.52f, height * 0.70f, width * 0.52f, height * 0.58f)
            close()

            // Nasal Cavity (Flared triangle)
            moveTo(width * 0.5f, height * 0.70f)
            cubicTo(width * 0.48f, height * 0.74f, width * 0.46f, height * 0.78f, width * 0.46f, height * 0.82f)
            lineTo(width * 0.54f, height * 0.82f)
            cubicTo(width * 0.54f, height * 0.78f, width * 0.52f, height * 0.74f, width * 0.5f, height * 0.70f)
            close()
        }

        drawPath(path = whitePartsPath, color = Color.White, style = Fill)
    }
}

@Preview(showBackground = true)
@Composable
fun SkullComponentPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        SkullComponent()
    }
}
