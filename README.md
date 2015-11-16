# Nestedset

[![Build
Status](https://travis-ci.org/ticketbis/grails-nestedset.png?branch=master)](https://travis-ci.org/ticketbis/grails-nestedset)

The nested set model is a particular technique for representing nested sets (also known as trees or hierarchies) in relational databases. This plugin provides nestedset behaviour to domain classes.

## Installation

Add dependency to your BuildConfig;

```groovy
compile "com.ticketbis:nestedset:0.1.0"
```

## Usage

The following example shows how to add nestedset behaviour to a domain class:

```groovy
// grails-app/domain
import com.ticketbis.nestedset.ast.Nestedset

@Nestedset
class Category {
    String name
}
```

Properties added to domain class are listed below:
```groovy
Integer lft // Nestedset left value
Integer rgt // Nestedset right value
Integer depth // Node depth inside the tree
Category parent // Parent node
```

Methods added to domain class are listed below:
```groovy
Boolean isLeaf() // does not have children.
Boolean isRootNode() // does not have parent node.
List getDescendants() // Its descendants. A subtree with the node as root.
Long countDescendants() // Number of descendants.
List getLeafs() // Descendants without children (Leafs nodes)
List getAncestors(boolean include_itself=false) // Ancestors (breadcrumb)
def getChildren(params=[:]) // Direct children
Long countChildren() // Count direct children
Category getLastChild() // Last child node
Category getRoot() // Its root node
Boolean isDescendant(Category node) 
```

Three static methods added to domain classes:
```groovy
// Adding nodes to the tree
Category.addNode(Category node, Category parent)
Category.addNode(Category node) // node.parent must be setted first otherwise will be a root node
// Removing nodes from the tree
Category.deleteNode(Category node)
// Moving one node (changing its parent)
Category.moveNode(Category node, Category newParent)
```

#### Customizing fallbacks


#### Known issues
addNode, deleteNode and moveNode methods may change lft, rgt and depth values of other nodes, so it is recommended to refresh them within the same session when you need access to one of these properties on other nodes.

The following example shows how parent properties are not updated:
```groovy
Category.addNode(parent)
Category.addNode(category, parent)
Category.addNode(category2, parent)
Category.addNode(category3, category2)

assert parent.rgt == 6 // Wrong value. parent.rgt value wasn't updated after Category.addNode(category3, category2)
parent.refresh()
assert parent.rgt == 8 // Right value.
```
