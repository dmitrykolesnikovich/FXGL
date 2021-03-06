/*
 * FXGL - JavaFX Game Library. The MIT License (MIT).
 * Copyright (c) AlmasB (almaslvl@gmail.com).
 * See LICENSE for details.
 */

package com.almasb.fxgl.physics.box2d.dynamics;

import com.almasb.fxgl.core.math.Vec2;
import com.almasb.fxgl.physics.box2d.collision.AABB;
import com.almasb.fxgl.physics.box2d.collision.RayCastInput;
import com.almasb.fxgl.physics.box2d.collision.RayCastOutput;
import com.almasb.fxgl.physics.box2d.collision.broadphase.BroadPhase;
import com.almasb.fxgl.physics.box2d.collision.shapes.MassData;
import com.almasb.fxgl.physics.box2d.collision.shapes.Shape;
import com.almasb.fxgl.physics.box2d.collision.shapes.ShapeType;
import com.almasb.fxgl.physics.box2d.common.JBoxUtils;
import com.almasb.fxgl.physics.box2d.common.Transform;
import com.almasb.fxgl.physics.box2d.dynamics.contacts.Contact;
import com.almasb.fxgl.physics.box2d.dynamics.contacts.ContactEdge;

/**
 * A fixture is used to attach a shape to a body for collision detection. A fixture inherits its
 * transform from its parent. Fixtures hold additional non-geometric data such as friction,
 * collision filters, etc. Fixtures are created via Body::CreateFixture.
 * Note: you cannot reuse fixtures.
 *
 * @author daniel
 */
public class Fixture {

    private final Body body;
    private final Shape shape;

    private final Filter filter = new Filter();

    private Object userData = null;

    private float density = 0;
    private float friction = 0;
    private float restitution = 0;
    private boolean isSensor = false;

    public FixtureProxy[] m_proxies;
    private int proxyCount = 0;

    Fixture(Body body, FixtureDef def) {
        this.body = body;

        userData = def.getUserData();
        friction = def.getFriction();
        restitution = def.getRestitution();
        density = def.getDensity();
        isSensor = def.isSensor();

        filter.set(def.getFilter());
        shape = def.getShape().clone();

        // Reserve proxy space
        int childCount = shape.getChildCount();

        m_proxies = new FixtureProxy[childCount];
        for (int i = 0; i < childCount; i++) {
            m_proxies[i] = new FixtureProxy();
            m_proxies[i].fixture = null;
            m_proxies[i].proxyId = BroadPhase.NULL_PROXY;
        }

        if (m_proxies.length < childCount) {
            FixtureProxy[] old = m_proxies;
            int newLen = JBoxUtils.max(old.length * 2, childCount);
            m_proxies = new FixtureProxy[newLen];
            System.arraycopy(old, 0, m_proxies, 0, old.length);
            for (int i = 0; i < newLen; i++) {
                if (i >= old.length) {
                    m_proxies[i] = new FixtureProxy();
                }
                m_proxies[i].fixture = null;
                m_proxies[i].proxyId = BroadPhase.NULL_PROXY;
            }
        }
        proxyCount = 0;
    }

    /**
     * Get the type of the child shape. You can use this to down cast to the concrete shape.
     *
     * @return the shape type
     */
    public ShapeType getType() {
        return shape.getType();
    }

    /**
     * Get the child shape. You can modify the child shape, however you should not change the number
     * of vertices because this will crash some collision caching mechanisms.
     *
     * @return child shape
     */
    public Shape getShape() {
        return shape;
    }

    /**
     * Is this fixture a sensor (non-solid)?
     *
     * @return true if the shape is a sensor.
     */
    public boolean isSensor() {
        return isSensor;
    }

    /**
     * Set if this fixture is a sensor.
     *
     * @param sensor sensor flag
     */
    public void setSensor(boolean sensor) {
        if (sensor != isSensor) {
            body.setAwake(true);
            isSensor = sensor;
        }
    }

    /**
     * Set the contact filtering data. This is an expensive operation and should not be called
     * frequently. This will not update contacts until the next time step when either parent body is
     * awake. This automatically calls refilter.
     *
     * @param filter filter
     */
    public void setFilterData(final Filter filter) {
        this.filter.set(filter);

        refilter();
    }

    /**
     * @return the contact filtering data
     */
    public Filter getFilterData() {
        return filter;
    }

    public int getProxyCount() {
        return proxyCount;
    }

    public FixtureProxy[] getProxies() {
        return m_proxies;
    }

    /**
     * Call this if you want to establish collision that was previously disabled by
     * ContactFilter::ShouldCollide.
     */
    public void refilter() {
        // Flag associated contacts for filtering.
        ContactEdge edge = body.getContactList();
        while (edge != null) {
            Contact contact = edge.contact;
            Fixture fixtureA = contact.getFixtureA();
            Fixture fixtureB = contact.getFixtureB();
            if (fixtureA == this || fixtureB == this) {
                contact.flagForFiltering();
            }
            edge = edge.next;
        }

        World world = body.getWorld();

        if (world == null) {
            return;
        }

        // Touch each proxy so that new pairs may be created
        BroadPhase broadPhase = world.m_contactManager.m_broadPhase;
        for (int i = 0; i < proxyCount; ++i) {
            broadPhase.touchProxy(m_proxies[i].proxyId);
        }
    }

    /**
     * Get the parent body of this fixture.
     *
     * @return the parent body
     */
    public Body getBody() {
        return body;
    }

    public void setDensity(float density) {
        assert (density >= 0f);
        this.density = density;
    }

    /**
     * @return density
     */
    public float getDensity() {
        return density;
    }

    /**
     * Get the user data that was assigned in the fixture definition. Use this to store your
     * application specific data.
     *
     * @return user data
     */
    public Object getUserData() {
        return userData;
    }

    /**
     * Set the user data. Use this to store your application specific data.
     *
     * @param data user data
     */
    public void setUserData(Object data) {
        userData = data;
    }

    /**
     * Test a point for containment in this fixture. This only works for convex shapes.
     *
     * @param p a point in world coordinates
     */
    public boolean containsPoint(final Vec2 p) {
        return shape.testPoint(body.m_xf, p);
    }

    /**
     * Cast a ray against this shape.
     *
     * @param output the ray-cast results
     * @param input the ray-cast input parameters
     */
    public boolean raycast(RayCastOutput output, RayCastInput input, int childIndex) {
        return shape.raycast(output, input, body.m_xf, childIndex);
    }

    /**
     * Get the mass data for this fixture. The mass data is based on the density and the shape. The
     * rotational inertia is about the shape's origin.
     */
    public void getMassData(MassData massData) {
        shape.computeMass(massData, density);
    }

    /**
     * @return the coefficient of friction
     */
    public float getFriction() {
        return friction;
    }

    /**
     * Set the coefficient of friction. This will <b>NOT</b> change the friction of existing contacts.
     *
     * @param friction friction
     */
    public void setFriction(float friction) {
        this.friction = friction;
    }

    /**
     * @return the coefficient of restitution
     */
    public float getRestitution() {
        return restitution;
    }

    /**
     * Set the coefficient of restitution. This will <b>NOT</b> change the restitution of existing
     * contacts.
     *
     * @param restitution restitution
     */
    public void setRestitution(float restitution) {
        this.restitution = restitution;
    }

    /**
     * Get the fixture's AABB. This AABB may be enlarge and/or stale. If you need a more accurate
     * AABB, compute it using the shape and the body transform.
     *
     * @return AABB
     */
    public AABB getAABB(int childIndex) {
        assert (childIndex >= 0 && childIndex < proxyCount);
        return m_proxies[childIndex].aabb;
    }

    /**
     * Compute the distance from this fixture.
     *
     * @param p a point in world coordinates.
     * @return distance
     */
    public float computeDistance(Vec2 p, int childIndex, Vec2 normalOut) {
        return shape.computeDistanceToOut(body.getTransform(), p, childIndex, normalOut);
    }

    void destroy() {
        // The proxies must be destroyed before calling this.
        assert (proxyCount == 0);

        m_proxies = null;
    }

    // These support body activation/deactivation.
    public void createProxies(BroadPhase broadPhase, final Transform xf) {
        assert (proxyCount == 0);

        // Create proxies in the broad-phase.
        proxyCount = shape.getChildCount();

        for (int i = 0; i < proxyCount; ++i) {
            FixtureProxy proxy = m_proxies[i];
            shape.computeAABB(proxy.aabb, xf, i);
            proxy.proxyId = broadPhase.createProxy(proxy.aabb, proxy);
            proxy.fixture = this;
            proxy.childIndex = i;
        }
    }

    /**
     * @param broadPhase broad phase
     */
    void destroyProxies(BroadPhase broadPhase) {
        // Destroy proxies in the broad-phase.
        for (int i = 0; i < proxyCount; ++i) {
            FixtureProxy proxy = m_proxies[i];
            broadPhase.destroyProxy(proxy.proxyId);
            proxy.proxyId = BroadPhase.NULL_PROXY;
        }

        proxyCount = 0;
    }

    private final AABB pool1 = new AABB();
    private final AABB pool2 = new AABB();
    private final Vec2 displacement = new Vec2();

    /**
     * @param broadPhase broad phase
     * @param transform1 xf1
     * @param transform2 xf2
     */
    void synchronize(BroadPhase broadPhase, final Transform transform1,
                     final Transform transform2) {
        if (proxyCount == 0) {
            return;
        }

        for (int i = 0; i < proxyCount; ++i) {
            FixtureProxy proxy = m_proxies[i];

            // Compute an AABB that covers the swept shape (may miss some rotation effect).
            final AABB aabb1 = pool1;
            final AABB aab = pool2;
            shape.computeAABB(aabb1, transform1, proxy.childIndex);
            shape.computeAABB(aab, transform2, proxy.childIndex);

            proxy.aabb.lowerBound.x =
                    aabb1.lowerBound.x < aab.lowerBound.x ? aabb1.lowerBound.x : aab.lowerBound.x;
            proxy.aabb.lowerBound.y =
                    aabb1.lowerBound.y < aab.lowerBound.y ? aabb1.lowerBound.y : aab.lowerBound.y;
            proxy.aabb.upperBound.x =
                    aabb1.upperBound.x > aab.upperBound.x ? aabb1.upperBound.x : aab.upperBound.x;
            proxy.aabb.upperBound.y =
                    aabb1.upperBound.y > aab.upperBound.y ? aabb1.upperBound.y : aab.upperBound.y;
            displacement.x = transform2.p.x - transform1.p.x;
            displacement.y = transform2.p.y - transform1.p.y;

            broadPhase.moveProxy(proxy.proxyId, proxy.aabb, displacement);
        }
    }
}
