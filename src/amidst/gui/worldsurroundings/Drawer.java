package amidst.gui.worldsurroundings;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

import amidst.fragment.Fragment;
import amidst.fragment.FragmentGraph;
import amidst.fragment.FragmentGraphItem;
import amidst.fragment.drawer.FragmentDrawer;
import amidst.gui.widget.Widget;
import amidst.resources.ResourceLoader;

public class Drawer {
	private static final BufferedImage DROP_SHADOW_BOTTOM_LEFT = ResourceLoader
			.getImage("dropshadow/inner_bottom_left.png");
	private static final BufferedImage DROP_SHADOW_BOTTOM_RIGHT = ResourceLoader
			.getImage("dropshadow/inner_bottom_right.png");
	private static final BufferedImage DROP_SHADOW_TOP_LEFT = ResourceLoader
			.getImage("dropshadow/inner_top_left.png");
	private static final BufferedImage DROP_SHADOW_TOP_RIGHT = ResourceLoader
			.getImage("dropshadow/inner_top_right.png");
	private static final BufferedImage DROP_SHADOW_BOTTOM = ResourceLoader
			.getImage("dropshadow/inner_bottom.png");
	private static final BufferedImage DROP_SHADOW_TOP = ResourceLoader
			.getImage("dropshadow/inner_top.png");
	private static final BufferedImage DROP_SHADOW_LEFT = ResourceLoader
			.getImage("dropshadow/inner_left.png");
	private static final BufferedImage DROP_SHADOW_RIGHT = ResourceLoader
			.getImage("dropshadow/inner_right.png");

	private final Object drawLock = new Object();

	private final AffineTransform originalLayerMatrix = new AffineTransform();
	private final AffineTransform layerMatrix = new AffineTransform();

	private final Map map;
	private final Movement movement;
	private final Zoom zoom;
	private final FragmentGraph graph;
	private final List<Widget> widgets;
	private final Iterable<FragmentDrawer> drawers;

	private Graphics2D g2d;
	private int viewerWidth;
	private int viewerHeight;
	private Point mousePosition;
	private FontMetrics widgetFontMetrics;

	private long lastTime = System.currentTimeMillis();
	private float time;

	public Drawer(Map map, Movement movement, Zoom zoom, FragmentGraph graph,
			List<Widget> widgets, Iterable<FragmentDrawer> drawers) {
		this.map = map;
		this.movement = movement;
		this.zoom = zoom;
		this.graph = graph;
		this.widgets = widgets;
		this.drawers = drawers;
	}

	public void drawCaptureImage(Graphics2D g2d, int viewerWidth,
			int viewerHeight, Point mousePosition, FontMetrics widgetFontMetrics) {
		synchronized (drawLock) {
			this.g2d = g2d;
			this.viewerWidth = viewerWidth;
			this.viewerHeight = viewerHeight;
			this.mousePosition = mousePosition;
			this.widgetFontMetrics = widgetFontMetrics;
			this.time = 0;
			updateMap();
			clear();
			drawMap();
			drawWidgets();
		}
	}

	public void draw(Graphics2D g2d, int viewerWidth, int viewerHeight,
			Point mousePosition, FontMetrics widgetFontMetrics) {
		synchronized (drawLock) {
			this.g2d = g2d;
			this.viewerWidth = viewerWidth;
			this.viewerHeight = viewerHeight;
			this.mousePosition = mousePosition;
			this.widgetFontMetrics = widgetFontMetrics;
			this.time = calculateTimeSpanSinceLastDrawInSeconds();
			updateZoom();
			updateMovement();
			updateMap();
			clear();
			drawMap();
			drawBorder();
			drawWidgets();
		}
	}

	private float calculateTimeSpanSinceLastDrawInSeconds() {
		long currentTime = System.currentTimeMillis();
		float result = Math.min(Math.max(0, currentTime - lastTime), 100) / 1000.0f;
		lastTime = currentTime;
		return result;
	}

	private void updateZoom() {
		zoom.update(map);
	}

	private void updateMovement() {
		movement.update(map, mousePosition);
	}

	private void updateMap() {
		map.setViewerDimensions(viewerWidth, viewerHeight);
		map.processTasks();
		map.adjustNumberOfRowsAndColumns();
	}

	private void clear() {
		g2d.setColor(Color.black);
		g2d.fillRect(0, 0, viewerWidth, viewerHeight);
	}

	private void drawMap() {
		// TODO: is this needed?
		Graphics2D old = g2d;
		g2d = (Graphics2D) old.create();
		doDrawMap();
		g2d = old;
	}

	private void doDrawMap() {
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		AffineTransform originalGraphicsTransform = g2d.getTransform();
		initOriginalLayerMatrix(originalGraphicsTransform);
		drawLayers();
		g2d.setTransform(originalGraphicsTransform);
	}

	private void initOriginalLayerMatrix(
			AffineTransform originalGraphicsTransform) {
		double scale = zoom.getCurrentValue();
		originalLayerMatrix.setTransform(originalGraphicsTransform);
		originalLayerMatrix.translate(map.getLeftOnScreen(),
				map.getTopOnScreen());
		originalLayerMatrix.scale(scale, scale);
	}

	private void drawLayers() {
		for (FragmentDrawer drawer : drawers) {
			if (drawer.isEnabled()) {
				initLayerMatrix();
				for (FragmentGraphItem fragmentGraphItem : graph) {
					Fragment fragment = fragmentGraphItem.getFragment();
					if (fragment.isLoaded()) {
						setAlphaComposite(fragment.getAlpha());
						g2d.setTransform(layerMatrix);
						drawer.draw(fragment, g2d, time);
					}
					updateLayerMatrix(fragmentGraphItem,
							graph.getFragmentsPerRow());
				}
			}
		}
		setAlphaComposite(1.0f);
	}

	private void initLayerMatrix() {
		layerMatrix.setTransform(originalLayerMatrix);
	}

	private void updateLayerMatrix(FragmentGraphItem fragmentGraphItem,
			int fragmentsPerRow) {
		layerMatrix.translate(Fragment.SIZE, 0);
		if (fragmentGraphItem.isEndOfLine()) {
			layerMatrix.translate(-Fragment.SIZE * fragmentsPerRow,
					Fragment.SIZE);
		}
	}

	private void drawBorder() {
		int width10 = viewerWidth - 10;
		int height10 = viewerHeight - 10;
		int width20 = viewerWidth - 20;
		int height20 = viewerHeight - 20;
		g2d.drawImage(DROP_SHADOW_TOP_LEFT, 0, 0, null);
		g2d.drawImage(DROP_SHADOW_TOP_RIGHT, width10, 0, null);
		g2d.drawImage(DROP_SHADOW_BOTTOM_LEFT, 0, height10, null);
		g2d.drawImage(DROP_SHADOW_BOTTOM_RIGHT, width10, height10, null);
		g2d.drawImage(DROP_SHADOW_TOP, 10, 0, width20, 10, null);
		g2d.drawImage(DROP_SHADOW_BOTTOM, 10, height10, width20, 10, null);
		g2d.drawImage(DROP_SHADOW_LEFT, 0, 10, 10, height20, null);
		g2d.drawImage(DROP_SHADOW_RIGHT, width10, 10, 10, height20, null);
	}

	private void drawWidgets() {
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		for (Widget widget : widgets) {
			if (widget.isVisible()) {
				setAlphaComposite(widget.getAlpha());
				widget.draw(g2d, viewerWidth, viewerHeight, mousePosition,
						widgetFontMetrics, time);
			}
		}
	}

	private void setAlphaComposite(float alpha) {
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
				alpha));
	}
}
