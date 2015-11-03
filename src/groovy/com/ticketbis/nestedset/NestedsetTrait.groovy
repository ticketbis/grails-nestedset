package com.ticketbis.nestedset

trait NestedsetTrait {

    Boolean isLeaf() {
        return this.rgt == this.lft + 1
    }

    Boolean isRootNode() {
        return this.parent == null
    }

    def getTree(exclude_itself=false) {
        if (this.isLeaf()) {
            return exclude_itself ? null : [this]
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

    def getDescendants() {
        return getTree(true)
    }

    Long countDescendants() {
        return ((this.rgt - this.lft - 1) / 2).longValue()
    }

    def getLeafs() {
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
        """
        
        return this.class.executeQuery(query, [this.id])
    }

    def getAncestors(include_itself=false) {
        if (this.isRootNode()) {
            if (include_itself) {
                return [this]
            }
            else {
                return []
            }
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

    def getChildren(params=[:]) {
        return this.class.findAllByParent(this, params)
    }

    Long countChildren() {
        return this.class.countByParent(this)
    }

    def getLastChild() {
        if (this.isLeaf()) {
            return null
        }
        return this.class.findByRgt(this.rgt - 1)
    }

    def getRoot() {
        if (this.isRootNode()) {
            return null
        }
        return this.class.findByParentIsNullAndLftLessThanAndRgtGreaterThan(this.lft, this.lft)
    }

    Boolean isDescendant(node) {
        return this.lft > node.lft && this.lft < node.rgt
    }


    /** 
    * Static methods
    **/

    /**
    * Since modifying the tree needs to be thread-safe, we need to lock the table
    * during any manipulation proccess. We use negative depth for root nodes to indicate
    * a lock.
    * This lock system can be improved for large tables by creating a special lock table.
    **/
    static void lockTree() {
        String cname = this.simpleName
        def res = this.executeUpdate("update ${cname} set depth = -1 where depth = 1")
        if (res == 0) {
            throw new NestedsetException("tree locked by other thread, try again ;)")
        }
    }

    static void unlockTree() {
        String cname = this.simpleName
        def res = this.executeUpdate("update ${cname} set depth = 1 where depth = -1")
        if (res == 0) {
            throw new NestedsetException("unlocking: nested tree corrupted")
        }
    }

    static addChild(node, parent=null) {
        this.withTransaction { status ->
            lockTree()

            if (parent) {
                def lastRight
                def nodeLeft
                def nodeRight
                if (parent.isLeaf()) {
                    node.depth = parent.depth + 1
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

                parent.refresh()
                parent.rgt += 2

                nodeLeft = node.lft
                nodeRight = node.rgt
                node.refresh()
                node.lft = nodeLeft
                node.rgt = nodeRight
            }
            else {
                def max = maxRightValue()
                node.depth = 1
                node.lft = max + 1
                node.rgt = max + 2
            }

            unlockTree()
        }
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

    static void move_right(rgt, delta) {
        String cname = this.simpleName
        def query_rgt = """
            update ${cname} node set node.rgt = node.rgt + ${delta},
            node.lastUpdated = ?
            where node.rgt > ?
        """
        def query_lft = """
            update ${cname} node set node.lft = node.lft + ${delta},
            node.lastUpdated = ?
            where node.lft > ?
        """
        def now = new Date()
        this.executeUpdate(query_rgt, [now, rgt])
        this.executeUpdate(query_lft, [now, rgt])

    }

    /**
    * deletes the node and all its descendants
    * param: leafSafe deletes only when node is a leaf
    **/
    static _deleteNode = { node, leafSafe=true ->
        if (leafSafe && !node.isLeaf()) {
            throw new NestedsetException("parent nodes cannot be deleted in leafSafe mode")
        }

        String cname = delegate.simpleName
        def width = node.rgt - node.lft + 1
        def query_del = """
            delete from ${cname} node where node.lft between ? and ? 
            order by node.depth desc
        """
        def query_rgt = """
            update ${cname} node set node.rgt = node.rgt - ${width},
            node.lastUpdated = ?
            where node.rgt > ?
        """
        def query_lft = """
            update ${cname} node set node.lft = node.lft - ${width},
            node.lastUpdated = ?
            where node.lft > ?
        """

        def now = new Date()
        delegate.withTransaction { status ->
            lockTree()

            delegate.executeUpdate(query_del, [node.lft, node.rgt])
            delegate.executeUpdate(query_rgt, [now, node.rgt])
            delegate.executeUpdate(query_lft, [now, node.rgt])

            unlockTree()
        }
    }

    /**
    * Moves the node and its descendants as child of the given parent node
    **/
    static _moveNode = { node, parent ->

        String cname = delegate.simpleName

        delegate.withTransaction { status ->
            if (parent.isDescendant(node)) {
                throw new NestedsetException("node cannot be moved to one of its descendants")
            }
            if (node.id == parent.id || node.parent && node.parent.id == parent.id) {
                return null
            }

            lockTree()

            node.parent = parent
            node.save(flush: true)
            
            // make the hole
            def gap = node.rgt - node.lft + 1
            move_right(parent.lft, gap)

            if (parent.lft < node.lft) {
                node.lft += gap
                node.rgt += gap
            }
            def nodeLeft = node.lft
            def nodeRight = node.rgt

            def depthDiff = parent.depth - node.depth + 1
            def jump = parent.lft - node.lft + 1
            
            // move the tree to the hole
            def sqlMove = """
                UPDATE ${cname}
                SET lft = lft + ${jump} ,
                    rgt = rgt + ${jump} ,
                    depth = depth + ${depthDiff},
                    lastUpdated = ?
                WHERE lft BETWEEN ${nodeLeft} AND ${nodeRight}
            """
            def now = new Date()

            delegate.executeUpdate(sqlMove, [now])

            closeGap(nodeLeft, nodeRight)

            unlockTree()
        }
    }

    static _closeGap = { lft, rgt ->
        String cname = delegate.simpleName
        def gapsize = rgt - lft + 1
        
        def sql_lft = "UPDATE ${cname} SET lft = lft - ${gapsize}, lastUpdated = ? WHERE lft > ${lft}"
        def sql_rgt = "UPDATE ${cname} SET rgt = rgt - ${gapsize}, lastUpdated = ? WHERE rgt > ${lft}"
        
        def now = new Date()
        delegate.executeUpdate(sql_lft, [now])
        delegate.executeUpdate(sql_rgt, [now])
    }


}
