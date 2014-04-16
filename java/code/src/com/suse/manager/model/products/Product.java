/**
 * Copyright (c) 2014 SUSE
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package com.suse.manager.model.products;

import com.redhat.rhn.frontend.struts.Selectable;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import java.util.LinkedList;
import java.util.List;

/**
 * A SUSE Product.
 */
@Root(strict = false)
public class Product implements Selectable, Comparable<Product> {
    /**
     * Aggregated product sync status.
     */
    public enum SyncStatus {
        /** Product has never been installed at all. */
        NOT_MIRRORED,
        /** Product has been installed, synchronization is in progress. */
        IN_PROGRESS,
        /** Product has been installed and synchronized completely. */
        FINISHED,
        /** Product installation or sync went bad. */
        FAILED;

        // Error message key
        private String messageKey;

        // Store additional debug information here
        private String details;

        /**
         * Returns a translation key for a status
         * @return the key
         */
        public String getTranslationKey() {
            return "setupwizard.syncstatus." + toString().replace("_", ".").toLowerCase();
        }

        /**
         * Get the error message key.
         * @return the message key
         */
        public String getMessageKey() {
            return messageKey;
        }

        /**
         * Set the error message key.
         * @param messageKey key of the message to set
         */
        public void setMessageKey(String messageIn) {
            this.messageKey = messageIn;
        }

        /**
         * Get additional debug information (if available).
         * @return the details
         */
        public String getDetails() {
            return details;
        }

        /**
         * Set any additional debug information.
         * @param details the details to set
         */
        public void setDetails(String detailsIn) {
            this.details = detailsIn;
        }
    };

    /** The architecture. */
    @Attribute
    private String arch;

    /** The ident ID. */
    @Attribute
    private String ident;

    /** The product readable name. */
    @Attribute
    private String name;

    /** The ident ID of the base product or an empty string. */
    @Attribute
    private String parent_product;

    /** The mandatory channels. */
    @Element(name = "mandatory_channels")
    private MandatoryChannels mandatoryChannels;

    /** The optional channels. */
    @Element(name = "optional_channels")
    private OptionalChannels optionalChannels;

    /** True if this product has been selected in the GUI. */
    private boolean selected = false;

    /** Base product or null. */
    private Product baseProduct = null;

    /** Addon products. */
    private List<Product> addonProducts = new LinkedList<Product>();

    /** Aggregated product sync status. */
    private SyncStatus syncStatus;

    /**
     * Default constructor.
     */
    public Product() {
        // required by Simple XML
    }

    /**
     * Instantiates a new product.
     *
     * @param archIn the architecture
     * @param identIn the ident ID
     * @param nameIn the name
     * @param baseProductIdent the ident ID of the base product or an empty string
     * the base product ident ID
     * @param mandatoryChannelsIn the mandatory channels in
     * @param optionalChannelsIn the optional channels in
     */
    public Product(String archIn, String identIn, String nameIn, String baseProductIdent,
            MandatoryChannels mandatoryChannelsIn, OptionalChannels optionalChannelsIn) {
        arch = archIn;
        ident = identIn;
        name = nameIn;
        parent_product = baseProductIdent;
        mandatoryChannels = mandatoryChannelsIn;
        optionalChannels = optionalChannelsIn;
    }

    /**
     * Gets the architecture.
     * @return the architecture
     */
    public String getArch() {
        return arch;
    }

    /**
     * Gets the ident ID.
     * @return the ident ID
     */
    public String getIdent() {
        return ident;
    }

    /**
     * Gets the name.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the base product's ident ID, if any.
     * @return the parent product ident ID or an empty string if this is a base
     * product
     */
    public String getBaseProductIdent() {
        return parent_product;
    }

    /**
     * Gets the mandatory channels.
     * @return the mandatory channels
     */
    public List<Channel> getMandatoryChannels() {
        return mandatoryChannels.getChannels();
    }

    /**
     * Gets the optional channels.
     * @return the optional channels
     */
    public List<Channel> getOptionalChannels() {
        return optionalChannels.getChannels();
    }

    /**
     * Check if all mandatory product channels are installed according to mgr-ncc-sync (P).
     * @return true if all channels are installed, otherwise false
     */
    public boolean isChannelsInstalled() {
        for (Channel c : getMandatoryChannels()) {
            if (!c.isInstalled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true iff this is a base product.
     *
     * @return true for base products
     */
    public boolean isBase() {
        return getBaseProductIdent().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(Product other) {
        return new CompareToBuilder()
        // base products first
        .append(!isBase(), !other.isBase())
        .append(this.name, other.getName())
        .append(this.arch, other.getArch())
        .append(this.ident, other.getIdent())
        .toComparison();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Product)) {
            return false;
        }
        Product otherProduct = (Product) other;
        return new EqualsBuilder()
            .append(getIdent(), otherProduct.getIdent())
            .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(getIdent())
            .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
        .append("Name", getName())
        .append("Arch", getArch())
        .append("Ident", getIdent())
        .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelectable() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelected() {
        return this.selected;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(boolean selectedIn) {
        this.selected = selectedIn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSelectionKey() {
        return this.ident;
    }

    /**
     * Gets the base product.
     * @return the base product
     */
    public Product getBaseProduct() {
        return baseProduct;
    }

    /**
     * Sets the base product.
     * @param baseProductIn the new base product
     */
    public void setBaseProduct(Product baseProductIn) {
        baseProduct = baseProductIn;
    }

    /**
     * Gets the addon products.
     * @return the addon products
     */
    public List<Product> getAddonProducts() {
        return addonProducts;
    }

    /**
     * Set the product sync status.
     * @param syncStatusIn the status
     */
    public void setSyncStatus(SyncStatus syncStatusIn) {
        this.syncStatus = syncStatusIn;
    }

    /**
     * Get the product sync status.
     * @return the sync status
     */
    public SyncStatus getSyncStatus() {
        return this.syncStatus;
    }
}
