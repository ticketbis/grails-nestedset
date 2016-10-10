package com.ticketbis.nestedset

trait NestedsetTrait {

    /**
    * Instance methods
    **/

    /**
    * does not have children
    **/
    boolean isLeaf() {
        return this.rgt == this.lft + 1
    }

    /**
    * Root node, does not have parent
    **/
    boolean isRootNode() {
        return this.parent == null
    }

    /**
    * Gets its descendants. A subtree with the node as root.
    **/
    List getTree(boolean exclude_itself=false) {
        if (this.isLeaf()) {
            return exclude_itself ? [] : [this]
        }
        else {
            String cname = this.class.name
            String exclude_hql = exclude_itself ? 'AND parent.id != node.id' : ''
            String query = """
                SELECT node
                FROM ${cname} node, ${cname} parent
                WHERE node.lft BETWEEN parent.lft AND parent.rgt
                AND parent.id = ?
                ${exclude_hql}
                ORDER BY node.lft
            """
            return this.class.executeQuery(query, [this.id])
        }
    }

    /**
    * Gets its descendants included itself sorted by lft
    **/
    List getDescendants() {
        return getTree(true)
    }

    Long countDescendants() {
        return ((this.rgt - this.lft - 1) / 2).longValue()
    }

    /**
    * Gets descendants without children (Leafs nodes)
    **/
    List getLeafs() {
        if (this.isLeaf()) {
            return []
        }
        String cname = this.class.name
        def query = """
            SELECT node
            FROM ${cname} node, ${cname} parent
            WHERE node.lft BETWEEN parent.lft AND parent.rgt
            AND parent.id = ?
            AND node.rgt = node.lft + 1
            ORDER BY node.lft
        """

        return this.class.executeQuery(query, [this.id])
    }

    /**
    * Gets its ancestors (its breadcrumb)
    **/
    List getAncestors(boolean include_itself=false) {
        if (this.isRootNode()) {
            return include_itself ? [this] : []
        }

        String cname = this.class.name
        String include_hql = include_itself ? '' : 'AND parent.id != node.id'
        String query = """
            SELECT parent
            FROM ${cname} AS node,
                    ${cname} AS parent
            WHERE node.lft BETWEEN parent.lft AND parent.rgt
            AND node.id = ?
            ${include_hql}
            ORDER BY parent.lft
        """
        return this.class.executeQuery(query, [this.id])
    }

    /**
    * Gets only its direct children
    **/
    def getChildren(params=[:]) {
        return this.class.findAllByParent(this, params)
    }

    Long countChildren() {
        return this.class.countByParent(this)
    }

    NestedsetTrait getLastChild() {
        if (this.isLeaf()) {
            return null
        }
        return this.class.findByRgt(this.rgt - 1)
    }

    NestedsetTrait getRoot() {
        if (this.isRootNode()) {
            return null
        }
        return this.class.findByParentIsNullAndLftLessThanAndRgtGreaterThan(this.lft, this.lft)
    }

    Boolean isDescendant(NestedsetTrait node) {
        return this.lft > node.lft && this.lft < node.rgt
    }

    /**
    * Static methods
    **/

	static def getRoots(params=[:]) {
		return this.findAllByParentIsNull(params)
	}

    /**
    * Since modifying the tree needs to be thread-safe, we need to lock the table
    * during any manipulation proccess. We use negative depth for root nodes to indicate
    * a lock.
    * This lock system can be improved for large tables by creating a special lock table.
    **/
    static void lockTree() {
        String cname = this.simpleName
        def res = this.executeUpdate("update ${cname} set depth = -1 where depth = 1")
        if (res == 0 && this.countByDepth(-1) > 0) {
            throw new NestedsetException("tree locked by other thread, try again ;)")
        }
    }

    static void unlockTree() {
        String cname = this.simpleName
        def res = this.executeUpdate("update ${cname} set depth = 1 where depth = -1")
        if (res == 0 && this.countByDepth(1) > 0) {
            throw new NestedsetException("unlocking: nested tree corrupted")
        }
    }

    static void addNode(NestedsetTrait node, NestedsetTrait parent) {
        node.parent = parent
        addNode(node)
    }

    static void addNode(NestedsetTrait node) {
        def parent = node.parent

        // nestedset properties could be changed by other node movements
        if (node.isAttached()) {
            node.refresh()
        }

        if (node.lft != 0 || node.rgt != 0 || node.depth != 0) {
            throw new NestedsetException("lft, rgt and depth properties cannot be setted explicitly")
        }

        if (parent?.isAttached()) {
            parent.refresh()
        }

        if (parent && parent.depth == 0) {
            throw new NestedsetException("parent node must be added to the tree first. Use addNode first")
        }

        node.nestedsetMutable = true
        this.withTransaction { status ->
            lockTree()
            node.save()

            if (parent) {
                def lastRight
                if (parent.isLeaf()) {
                    node.depth = parent.isRootNode() ? 2 : parent.depth + 1
                    node.lft = parent.lft + 1
                    node.rgt = parent.lft + 2
                    node.parent = parent

                    lastRight = parent.lft
                }
                else {
                    def lastChild = parent.getLastChild()
                    addSibling(node, lastChild, false)
                    lastRight = lastChild.rgt
                }

                move_right(lastRight, 2)

                def nodeLeft = node.lft
                def nodeRight = node.rgt
                node.refresh()
                node.lft = nodeLeft
                node.rgt = nodeRight
            }
            else {
                def max = maxRightValue()
                node.depth = -1 // restored to 1 when unlocked
                node.lft = max + 1
                node.rgt = max + 2
            }

            unlockTree()

            afterTreeChanged()
        }
        node.nestedsetMutable = false

        parent?.refresh()
    }

    static Long maxRightValue() {
        return this.createCriteria().get {
            projections {
                max "rgt"
            }
        } as Long
    }

    static void addSibling(node, sibling, persist=true) {
        node.depth = sibling.depth
        node.lft = sibling.rgt + 1
        node.rgt = sibling.rgt + 2
        node.parent = sibling.parent

        if (persist) {
            move_right(sibling.rgt, 2)
            node.save(flush: true)
        }

    }

    static void move_right(rgt, delta, updateVersion=true) {
        String cname = this.simpleName

        def versioned = this.getDeclaredField('version') != null
        String versionQuery = updateVersion && versioned ? ', node.version = node.version + 1' : ''

        def query_rgt = """
            update ${cname} node set node.rgt = node.rgt + ${delta}
            ${versionQuery}
            where node.rgt > ?
        """
        def query_lft = """
            update ${cname} node set node.lft = node.lft + ${delta}
            ${versionQuery}
            where node.lft > ?
        """
        this.executeUpdate(query_rgt, [rgt])
        this.executeUpdate(query_lft, [rgt])

    }

    /**
    * deletes the node and all its descendants
    * param: leafSafe deletes only when node is a leaf
    **/
    static void deleteNode(NestedsetTrait node, boolean leafSafe=true) {
        if (isNodeDirty(node)) {
            throw new NestedsetException("nestedset node properties cannot be modified manually")
        }

        // nestedset properties could be changed by other node movements
        node.refresh()

        if (leafSafe && !node.isLeaf()) {
            throw new NestedsetException("parent nodes cannot be deleted in leafSafe mode")
        }

        this.withTransaction { status ->
            lockTree()
            deleteQueries(node)
            unlockTree()

            afterTreeChanged()
        }
    }

    /**
    * Method required due to wrong meaning of `this` inside withTransaction
    **/
    static void deleteQueries(NestedsetTrait node){
        String cname = this.simpleName

        def versioned = this.getDeclaredField('version') != null
        String versionQuery = versioned ? ', node.version = node.version + 1' : ''

        def width = node.rgt - node.lft + 1
        def query_del = """
            delete from ${cname} node where node.lft between ? and ?
            order by node.depth desc
        """
        def query_rgt = """
            update ${cname} node set node.rgt = node.rgt - ${width}
            ${versionQuery}
            where node.rgt > ?
        """
        def query_lft = """
            update ${cname} node set node.lft = node.lft - ${width}
            ${versionQuery}
            where node.lft > ?
        """

        this.executeUpdate(query_del, [node.lft, node.rgt])
        this.executeUpdate(query_rgt, [node.rgt])
        this.executeUpdate(query_lft, [node.rgt])
    }

    static boolean isNodeDirty(NestedsetTrait node) {
        return node.isDirty('lft') || node.isDirty('rgt') || node.isDirty('depth')
    }

    /**
    * Moves the node and its descendants as child of the given parent node
    **/
    static void moveNode(NestedsetTrait node, NestedsetTrait parent) {
        if (isNodeDirty(node)) {
            throw new NestedsetException("nestedset node properties cannot be modified manually")
        }

        if (parent && isNodeDirty(parent)) {
            throw new NestedsetException("nestedset parent node properties cannot be modified manually")
        }

        // nestedset properties could be changed by other node movements
        if (node.isAttached()) {
            node.refresh()
        }

        if (parent) {
            parent.refresh()
        }

        if (parent?.isDescendant(node)) {
            throw new NestedsetException("node cannot be moved to one of its descendants")
        }

        if (node.id == parent?.id || node.parent?.id == parent?.id) {
            return
        }

        def versioned = this.getDeclaredField('version') != null
        String versionQuery = versioned ? ', version = version + 1' : ''

        node.nestedsetMutable = true
        this.withTransaction { status ->
            lockTree()

            node.parent = parent

            if (parent) {
                // make the hole
                def gap = node.rgt - node.lft + 1
                move_right(parent.lft, gap, false)

                if (parent.lft < node.lft) {
                    node.lft += gap
                    node.rgt += gap
                }
            }

            def nodeLeft = node.lft
            def nodeRight = node.rgt

            node.save(flush: true)

            def newParentDepth = parent ? parent.depth : 0
            def newParentLft = parent ? parent.lft : maxRightValue()
            moveQueries(node, newParentDepth, newParentLft, versionQuery)

            closeGap(nodeLeft, nodeRight, versionQuery)

            node.refresh()
            touchDescendants(node)

            if (!parent) {
                // forces new root node to depth -1 before unlocked
                forceRootDepth(node)
            }

            unlockTree()

            afterTreeChanged()
        }
        node.nestedsetMutable = false

        node.refresh()
        parent?.refresh()
    }

    static void touchDescendants(NestedsetTrait node) {
        if (this.getDeclaredField('lastUpdated') != null) {
            String cname = this.simpleName
            String sqlTouchDescendants = """
                UPDATE ${cname}
                SET lastUpdated = current_timestamp
                WHERE lft > :left AND rgt < :right
            """
            def params = [left: node.lft, right: node.rgt]
            this.executeUpdate(sqlTouchDescendants, params)
        }
    }

    /**
    * Method required due to wrong meaning of `this` inside withTransaction
    **/
    static void moveQueries(NestedsetTrait node, def parentDepth, def parentLft, String versionQuery) {
        def depthDiff = parentDepth - node.depth + 1
        def jump = parentLft - node.lft + 1

        String cname = this.simpleName
        // move the tree to the hole
        def sqlMove = """
            UPDATE ${cname}
            SET lft = lft + ${jump} ,
                rgt = rgt + ${jump} ,
                depth = depth + ${depthDiff}
                ${versionQuery}
            WHERE lft BETWEEN ${node.lft} AND ${node.rgt}
        """

        this.executeUpdate(sqlMove)
    }

    static void forceRootDepth(NestedsetTrait node) {
        String cname = this.simpleName
        this.executeUpdate("UPDATE ${cname} SET depth = -1 WHERE id = ${node.id}")
    }

    static void closeGap(Integer lft, Integer rgt, String versionQuery) {
        String cname = this.simpleName
        def gapsize = rgt - lft + 1

        def sql_lft = "UPDATE ${cname} SET lft = lft - ${gapsize}${versionQuery} WHERE lft > ${lft}"
        def sql_rgt = "UPDATE ${cname} SET rgt = rgt - ${gapsize}${versionQuery} WHERE rgt > ${lft}"

        this.executeUpdate(sql_lft)
        this.executeUpdate(sql_rgt)
    }

    void b4Insert() {
        if (!this.nestedsetMutable) {
            throw new NestedsetException("New nodes must be added by using static method addNode")
        }
    }

    void b4Update() {
        if (!this.nestedsetMutable) {
            if (this.isDirty('lft') || this.isDirty('rgt') || this.isDirty('depth')) {
                throw new NestedsetException("Nestedset properties (lft, rgt and depth) cannot be altered manually")
            }
        }
    }

    static void afterTreeChanged() { /* Do nothing */ }
}
