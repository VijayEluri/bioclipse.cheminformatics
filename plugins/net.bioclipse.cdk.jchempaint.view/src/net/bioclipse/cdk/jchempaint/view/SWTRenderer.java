/*******************************************************************************
 * Copyright (c) 2009  Arvid Berg <goglepox@users.sourceforge.net>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * <http://www.eclipse.org/legal/epl-v10.html>
 *
 * Contact: http://www.bioclipse.net/
 ******************************************************************************/
package net.bioclipse.cdk.jchempaint.view;

import java.awt.geom.AffineTransform;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import net.bioclipse.cdk.jchempaint.rendering.Renderer;

import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Point;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.elements.ArrowElement;
import org.openscience.cdk.renderer.elements.AtomSymbolElement;
import org.openscience.cdk.renderer.elements.ElementGroup;
import org.openscience.cdk.renderer.elements.GeneralPath;
import org.openscience.cdk.renderer.elements.IRenderingElement;
import org.openscience.cdk.renderer.elements.LineElement;
import org.openscience.cdk.renderer.elements.OvalElement;
import org.openscience.cdk.renderer.elements.PathElement;
import org.openscience.cdk.renderer.elements.RectangleElement;
import org.openscience.cdk.renderer.elements.TextElement;
import org.openscience.cdk.renderer.elements.TextGroupElement;
import org.openscience.cdk.renderer.elements.WedgeLineElement;
import org.openscience.cdk.renderer.font.IFontManager;
import org.openscience.cdk.renderer.font.SWTFontManager;
import org.openscience.cdk.renderer.generators.BasicBondGenerator.WedgeWidth;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator.BackgroundColor;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator.ForegroundColor;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator.Scale;
import org.openscience.cdk.renderer.generators.ExtendedAtomGenerator.ShowImplicitHydrogens;
import org.openscience.cdk.renderer.generators.ReactionSceneGenerator.ArrowHeadWidth;
import org.openscience.cdk.renderer.visitor.IDrawVisitor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;

public class SWTRenderer implements IDrawVisitor{

    private Logger logger = Logger.getLogger( SWTRenderer.class );

    private GC gc;

    private RendererModel model;

    private AffineTransform transform;

    private SWTFontManager fontManager;

    private Map<java.awt.Color, Color> cleanUp;

    public SWTRenderer(GC graphics) {
        transform = new AffineTransform();
        this.model = new RendererModel();
        this.gc = graphics;
    }

    private enum Type {
        Forground,Background
    }

    private void setForeground(java.awt.Color color) {
        setColor(Type.Forground,color);
    }

    private void setBackground(java.awt.Color color) {
        setColor(Type.Background,color);
    }

    private void setColor(Type type,java.awt.Color color) {
        Color c = toSWTColor( gc, color );
        int alpha = color.getAlpha();
        switch ( type ) {
            case Forground:
                gc.setForeground( c );
                gc.setAlpha( alpha );
                break;
            case Background:
                gc.setBackground( c );
                gc.setAlpha( alpha );
                break;
        }
    }

    public RendererModel getModel() {
        return model;
    }

    @Deprecated
    private int transformX(double x) {
        return (int) transform( x, 1 )[0];
    }

    @Deprecated
    private int transformY(double y) {
        return (int) transform( 1, y )[1];
    }

    private Point transformXY(double x, double y) {
    	double[] vals = transform(x,y);
    	return new Point((int)(vals[0]+.5),(int)(vals[1]+.5));
    }
    private double[] transform(double x, double y) {
        double [] result = new double[2];
        transform.transform( new double[] {x,y}, 0, result, 0, 1 );
        return result;
    }

    private float[] transform(float[] values) {
        float[] result = new float[values.length];
        transform.transform( values, 0, result, 0, result.length/2 );
        return result;
    }

    private int scaleX(double x) {
        return (int) (x*transform.getScaleX());
    }

    private int scaleY(double y) {
        return (int) (y*transform.getScaleY());
    }

    public void render(ElementGroup renderingModel) {
        for (IRenderingElement re : renderingModel) {
           re.accept( this );
        }
    }


    public void visit( OvalElement element ) {
        Color colorOld = gc.getBackground();
        int radius = scaleX(element.radius);
        int diameter = scaleX(element.radius * 2);

        Point p = transformXY(element.xCoord,element.yCoord);
        if (element.fill) {
            setBackground(element.color);

            gc.fillOval(p.x - radius,
                        p.y - radius,
                        diameter,
                        diameter );
        } else {
            setForeground(element.color);

            gc.drawOval(p.x - radius,
                        p.y - radius,
                        diameter,
                        diameter );
        }
        gc.setBackground( colorOld);
    }

    public void visit( LineElement element ) {
        Color bColorOld = gc.getBackground();
        Color fColorOld = gc.getForeground();
        int oldLineWidth = gc.getLineWidth();
        // init recursion with background to get the first draw with foreground
        setForeground( element.color );
        double bondWidth = scaleX(element.width);
        gc.setLineWidth( (int) (bondWidth<1?1:bondWidth) );
        drawLine( element );

        gc.setLineWidth( oldLineWidth );
        gc.setBackground( bColorOld);
        gc.setForeground( fColorOld );
    }

    public void visit( WedgeLineElement element) {
        Color colorOld = gc.getBackground();
        setForeground( getForgroundColor() );
        setBackground( getForgroundColor() );
        //drawWedge( element);
        drawWedge( element );
        gc.setBackground( colorOld );
    }

    private void drawWedge(WedgeLineElement wedge) {

        Vector2d normal =
            new Vector2d(wedge.firstPointY - wedge.secondPointY, wedge.secondPointX - wedge.firstPointX);
        normal.normalize();
        normal.scale(model.getParameter(WedgeWidth.class).getValue() /
        		     model.getParameter(Scale.class).getValue());

        // make the triangle corners
        Point2d vertexA = new Point2d(wedge.firstPointX, wedge.firstPointY);
        Point2d vertexB = new Point2d(wedge.secondPointX, wedge.secondPointY);
        Point2d vertexC = new Point2d(vertexB);
        vertexB.add(normal);
        vertexC.sub(normal);

        if ( wedge.type == WedgeLineElement.TYPE.DASHED )
            drawDashedWedge( vertexA, vertexB, vertexC);
        else
            drawFilledWedge(vertexA, vertexB, vertexC);

    }

    private void drawFilledWedge(Point2d pA, Point2d pB, Point2d pC) {
        double[] a = transform(pA.x, pA.y);
        double[] b = transform(pB.x, pB.y);
        double[] c = transform(pC.x, pC.y);

        Path path = new Path(gc.getDevice());
        path.moveTo((float) a[0], (float) a[1]);
        path.lineTo((float) b[0], (float) b[1]);
        path.lineTo((float) c[0], (float) c[1]);
        path.close();

        gc.fillPath( path );

        path.dispose();
    }

    private void drawDashedWedge(Point2d pA, Point2d pB, Point2d pC) {
        // calculate the distances between lines
        double distance = pB.distance(pA);
        double gapFactor = 0.075;
        double gap = distance * gapFactor;
        double numberOfDashes = distance / gap;
        double d = 0;

        // draw by interpolating along the edges of the triangle
        Path path = new Path(gc.getDevice());
        for (int i = 0; i < numberOfDashes; i++) {
            Point2d p1 = new Point2d();
            p1.interpolate(pA, pB, d);
            Point2d p2 = new Point2d();
            p2.interpolate(pA, pC, d);
            double[] p1T = transform(p1.x, p1.y);
            double[] p2T = transform(p2.x, p2.y);
            path.moveTo((float)p1T[0], (float)p1T[1]);
            path.lineTo((float)p2T[0], (float)p2T[1]);
            if (distance * (d + gapFactor) >= distance) {
                break;
            } else {
                d += gapFactor;
            }
        }
        gc.drawPath( path);
        path.dispose();
    }

    private java.awt.Color getForgroundColor() {
        return getModel().getParameter(ForegroundColor.class).getValue();
    }

    private java.awt.Color getBackgroundColor() {
        return getModel().getParameter(BackgroundColor.class)
        	.getValue();
    }


    private void drawLine(LineElement element) {
        Path path = new Path(gc.getDevice());
        double[] p1=transform( element.firstPointX, element.firstPointY );
        double[] p2=transform( element.secondPointX, element.secondPointY );
        path.moveTo( (float)p1[0], (float)p1[1] );
        path.lineTo( (float)p2[0], (float)p2[1] );
        gc.drawPath( path );
        path.dispose();
    }

    private Font getFont() {

        return fontManager.getFont();
    }

    private Font getSmallFont() {

        return fontManager.getSmallFont();
    }

    public void visit(ArrowElement element) {
        Path path = new Path(gc.getDevice());
        double[] a=transform( element.startX, element.startY );
        double[] b=transform( element.endX, element.endY );
        path.moveTo((float)a[0], (float)a[1]);
        path.lineTo((float)b[0], (float)b[1]);
        double aW = model.getParameter(ArrowHeadWidth.class).getValue()
            / model.getParameter(Scale.class).getValue();
        if (element.direction) {
            double[] c = transform( element.startX - aW, element.startY - aW );
            double[] d = transform( element.startX - aW, element.startY + aW );
            path.moveTo((float)a[0], (float)a[1]);
            path.lineTo((float)c[0], (float)c[1]);
            path.lineTo((float)a[0], (float)a[1]);
            path.lineTo((float)d[0], (float)d[1]);
        } else {
            double[] c = transform( element.endX + aW, element.endY - aW );
            double[] d = transform( element.endX + aW, element.endY + aW );
            path.moveTo((float)a[0], (float)a[1]);
            path.lineTo((float)c[0], (float)c[1]);
            path.lineTo((float)a[0], (float)a[1]);
            path.lineTo((float)d[0], (float)d[1]);
        }
        gc.drawPath( path );
        path.dispose();
    }

    public void visit( TextElement element ) {
    	Point p = transformXY(element.xCoord,element.yCoord);
        int x = p.x;
        int y = p.y;
        String text = element.text;

        gc.setFont(getFont());

        Point textSize = gc.textExtent( text );
        x = x - textSize.x/2;
        y = y - textSize.y/2;
        setForeground( element.color );
        setBackground(  getBackgroundColor() );
        gc.setAdvanced( true );
        gc.drawText( text, x, y, true );
    }

    public void visit( TextGroupElement element ) {
    	Point p = transformXY(element.xCoord,element.yCoord);
        int x = p.x;
        int y = p.y;
        String text = element.text;

        gc.setFont(getFont());

        Point textSize = gc.textExtent( text );
        x = x - textSize.x/2;
        y = y - textSize.y/2;
        setForeground( element.color );
        setBackground(  getBackgroundColor() );
        gc.setAdvanced( true );
        gc.drawText( text, x, y, false );
    }

    public void visit(AtomSymbolElement element) {
    	Point p = transformXY(element.xCoord,element.yCoord);
        int x = p.x;
        int y = p.y;

        String text = element.text;

        gc.setFont(getFont());

        Point textSize = gc.textExtent( text );
        x = x - textSize.x/2;
        y = y - textSize.y/2;
        setForeground( element.color );
        setBackground(  getBackgroundColor() );
        gc.setAdvanced( true );
        gc.drawText( text, x, y, false );

        Point secondTextSize = gc.textExtent( "H" );
        gc.setFont( getSmallFont() );
        Point cp = new Point(0,0);
        if(element.formalCharge!=0) {
            String fc = Integer.toString( element.formalCharge);
            fc = (element.formalCharge==1?"+":fc);
            fc = (element.formalCharge>1?"+"+fc:fc);
            fc = (element.formalCharge==-1?"-":fc);
            cp = gc.textExtent( fc );
            int fcX = x+textSize.x;
            int fcY = y-cp.y/2;
            gc.drawText( fc, fcX, fcY, true );
        }

        if(element.hydrogenCount >0 && model.getParameter(
        	ShowImplicitHydrogens.class).getValue()) {

            Point hc = new Point(0,0);
            if(element.hydrogenCount >1) {
                hc = gc.textExtent( Integer.toString( element.hydrogenCount ));
            }
            switch(element.alignment) {
                case -1: x = x -secondTextSize.x - hc.x;break;
                case 1:  x = x + textSize.x+cp.x;break;
                case -2: y = y + textSize.y;break;
                case 2:  y = y+cp.y/2 - Math.max( secondTextSize.y,secondTextSize.y/2 - hc.y);break;
            }
            if(element.hydrogenCount >1) {
                gc.drawText( Integer.toString( element.hydrogenCount),
                             x + secondTextSize.x, y + secondTextSize.y/2
                             ,true);
            }
            gc.setFont(getFont());
            gc.drawText( "H", x, y ,false);

        }
    }

    public Color toSWTColor(GC graphics,java.awt.Color color) {
        if (cleanUp == null) {
            cleanUp = new HashMap<java.awt.Color,Color>();
        }

        if (color == null) {
            return graphics.getDevice().getSystemColor(SWT.COLOR_MAGENTA);
        }

        assert(color != null);
        Color otherColor = cleanUp.get(color);
        if (otherColor == null) {
            otherColor = new Color(graphics.getDevice(),
                                   color.getRed(),
                                   color.getGreen(),
                                   color.getBlue());
            cleanUp.put(color,otherColor);
        }
        return otherColor;
    }

    public void dispose() {
        for (Color c : cleanUp.values()) {
            c.dispose();
        }
        cleanUp.clear();
    }

    public void visit( ElementGroup elementGroup ) {
        for (IRenderingElement element : elementGroup) {
            element.accept(this);
        }
    }

    public void setTransform(AffineTransform transform) {
        this.transform = transform;
    }

    public void visitDefault(IRenderingElement element) {
    	if(!visitOSGi(element))
        logger.debug("No visitor method implemented for : "
                + element.getClass());
    }

    public void visit(RectangleElement element) {

        if (element.filled) {
            setBackground(element.color);
            gc.fillRectangle(
                    transformX(element.xCoord), transformY(element.yCoord),
                    scaleX(element.width), scaleY(element.height));
        } else {
            setForeground( element.color );
            gc.drawRectangle(
                    transformX(element.xCoord), transformY(element.yCoord),
                    scaleX(element.width), scaleY(element.height));
        }
    }

    public void visit(PathElement element) {
        setForeground(element.color);
        Path path = new Path(gc.getDevice());
        boolean first = true;
        for(Point2d p: element.points) {
            double[] tp = transform( p.x, p.y );

            if(first) {
                path.moveTo( (float)tp[0], (float)tp[1]);
                first = false;
            } else {
                path.lineTo( (float)tp[0], (float)tp[1]);
            }
        }
        gc.drawPath( path );
        path.dispose();
    }

    public void visit(GeneralPath element) {
        setForeground( element.color );
        int oldStyle = gc.getLineStyle();
        gc.setLineStyle( SWT.LINE_DOT );
        Path path = new Path(gc.getDevice());

        for(org.openscience.cdk.renderer.elements.path.PathElement e:element.elements) {
            float[] v = transform( e.points());
            for(float f:v) assert(!Float.isNaN( f ));
            switch ( e.type() ) {
                case MoveTo: path.moveTo( v[0],v[1] );break;
                case LineTo: path.lineTo( v[0], v[1] );break;
                case QuadTo: path.quadTo( v[0], v[1], v[2], v[3] );break;
                case CubicTo: path.cubicTo( v[0], v[1], v[2], v[3], v[4], v[5] );break;
                case Close: path.close();
            }
        }
        gc.drawPath( path );
        path.dispose();
        gc.setLineStyle( oldStyle );
    }

    public void visit(IRenderingElement element)  {

        Method method = getMethod( element );
        if (method == null) {
            visitDefault(element);
        }
        else {
            try {
                method.invoke( this, new Object[] {element} );
            } catch ( IllegalArgumentException e ) {
                logger.debug(e.getMessage(), e );
                visitDefault( element );
            } catch ( IllegalAccessException e ) {
                logger.debug(e.getMessage(), e );
                visitDefault( element );
            } catch ( InvocationTargetException e ) {
                logger.debug(e.getMessage(), e );
                visitDefault( element );
            }
        }
    }

    private Method getMethod( IRenderingElement element ) {

        Class<?> cl = element.getClass();
        while ( !cl.equals( Object.class ) ) {
            try {
                return this.getClass().getDeclaredMethod( "visit",
                                                          new Class[] { cl } );

            } catch ( NoSuchMethodException e ) {
                cl = cl.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Tries to look up a renderer in the OSGi registry.
     * 
     * @param element
     * @return true if the element was rendered with a service
     */
    private boolean visitOSGi(IRenderingElement element) {
    	BundleContext context = FrameworkUtil.getBundle(SWTRenderer.class).getBundleContext();
    	try {
			Collection<ServiceReference<Renderer>> sRefs = context.getServiceReferences(Renderer.class, null);
			List<Renderer> renderers = new ArrayList<Renderer>();
			for(ServiceReference<Renderer> sRef:sRefs) {
				Renderer rer = context.getService(sRef);
				if(rer!=null)
					renderers.add(rer);
			}
			for(Renderer r:renderers) {
				if(r.accepts(element)) {
					r.visit(gc,transform,model, element);
					return true;
				}
			}
		} catch (InvalidSyntaxException e) {
			logger.warn("Could not lookup renderer", e);
		}
    	return false;
    }

    public void setFontManager( IFontManager fontManager ) {
        this.fontManager = (SWTFontManager) fontManager;
    }

    public void setRendererModel( RendererModel rendererModel ) {
        this.model = rendererModel;
    }
}
