package br.com.arrasavendas.entregas;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import br.com.arrasavendas.R;
import br.com.arrasavendas.Utilities;
import br.com.arrasavendas.model.Cidade;
import br.com.arrasavendas.model.Cliente;
import br.com.arrasavendas.model.FormaPagamento;
import br.com.arrasavendas.model.ItemVenda;
import br.com.arrasavendas.model.ServicoCorreios;
import br.com.arrasavendas.model.StatusVenda;
import br.com.arrasavendas.model.TurnoEntrega;
import br.com.arrasavendas.model.Venda;
import br.com.arrasavendas.providers.VendasProvider;

public class EntregasExpandableListAdapter extends BaseExpandableListAdapter {

    private static final long CORREIOS_KEY = Long.MAX_VALUE;
    private final int BLUE_LUCAS;
    private final int PINK_ADNA;
    private final int AMARELO_MCLARA;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy - EEEE", Locale.getDefault());
    private final Comparator<Venda> comparator = new VendaComparator();
    private Map<Long, List<Venda>> vendasPorDataDeEntrega;
    private List<Long> datasDeEntregas;
    private Context ctx;
    private List<Venda> vendas;
    private Cursor cursor;


    public EntregasExpandableListAdapter(Context ctx) {
        this.ctx = ctx;
        sdf.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));

        Resources resources = ctx.getResources();
        this.BLUE_LUCAS = resources.getColor(R.color.blueLucas);
        this.PINK_ADNA = resources.getColor(R.color.pinkAdna);
        this.AMARELO_MCLARA = resources.getColor(R.color.amareloMClara);
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;

        vendasPorDataDeEntrega = new HashMap<Long, List<Venda>>();
        this.vendas = getData();
        agruparVendasPorDataDeEntrega();
        notifyDataSetChanged();
    }

    public void removerVenda(Venda venda) {
        vendas.remove(venda);
        agruparVendasPorDataDeEntrega();
        notifyDataSetChanged();
    }

    public void refreshView() {
        agruparVendasPorDataDeEntrega();
        notifyDataSetChanged();
    }

    //    método responsável por agrupar venda por data de entrega
    private void agruparVendasPorDataDeEntrega() {

        datasDeEntregas = new LinkedList<Long>();

        for (Venda v : vendas) {
            Date dataEntrega = v.getDataEntrega();
            long time = 0;

            if (dataEntrega != null) {

                Calendar c = GregorianCalendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
                c.setTime(dataEntrega);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);

                time = c.getTimeInMillis();

            } else {
                // se a data de entrega da venda é nula, usa uma chave especial
                // para indexar vendas p/ outras cidades
                time = CORREIOS_KEY;
            }

            if (!datasDeEntregas.contains(time)) {
                datasDeEntregas.add(time);
                vendasPorDataDeEntrega.put(time, new LinkedList<Venda>());
            }

            vendasPorDataDeEntrega.get(time).add(v);
        }
        // ordenando as datas de entrega em ordem decrescente
        Collections.sort(datasDeEntregas, new Comparator<Long>() {
            @Override
            public int compare(Long lhs, Long rhs) {
                return -1 * lhs.compareTo(rhs);  // ordem decrescente
            }
        });

        // responsável por ordenar as vendas baseando-se no turno das mesmas, dentro de cada
        // grupo de data de entrega
        for (Long key : vendasPorDataDeEntrega.keySet()) {
            Collections.sort(vendasPorDataDeEntrega.get(key), comparator);
        }

    }

    private List<Venda> getData() {

        List<Venda> vendas = new LinkedList<Venda>();

        if (cursor != null && cursor.moveToFirst()) {
            do {

                Venda v = new Venda();

                Calendar dataEntrega = Calendar.getInstance();
                long aLong = cursor.getLong(cursor.getColumnIndex(VendasProvider.DATA_ENTREGA));
                if (aLong > 0) {
                    dataEntrega.setTimeInMillis(aLong);
                    v.setDataEntrega(dataEntrega.getTime());
                } // deixa null a data de entrega das vendas para outras cidades

                v.setId(cursor.getLong(cursor.getColumnIndex(VendasProvider._ID)));
                v.setVendedor(cursor.getString(cursor.getColumnIndex(VendasProvider.VENDEDOR)));

                String formaPagamento = cursor.getString(cursor.getColumnIndex(VendasProvider.FORMA_PAGAMENTO));
                v.setFormaDePagamento(FormaPagamento.valueOf(formaPagamento));

                int abatimentoEmCentavos = cursor.getInt(cursor.getColumnIndex(VendasProvider.ABATIMENTO));
                v.setAbatimentoEmCentavos(abatimentoEmCentavos);

                String status = cursor.getString(cursor.getColumnIndex(VendasProvider.STATUS));
                v.setStatus(StatusVenda.valueOf(status));

                String turnoEntrega = cursor.getString(cursor.getColumnIndex(VendasProvider.TURNO_ENTREGA));
                v.setTurnoEntrega(TurnoEntrega.valueOf(turnoEntrega));

                String tipoFrete = cursor.getString(cursor.getColumnIndex(VendasProvider.SERVICO_CORREIOS));
                if (!TextUtils.isEmpty(tipoFrete.trim()))
                    v.setServicoCorreios(ServicoCorreios.valueOf(tipoFrete));

                v.setFreteEmCentavos(cursor.getLong(cursor.getColumnIndex(VendasProvider.FRETE)));
                v.setCodigoRastreio(cursor.getString(cursor.getColumnIndex(VendasProvider.CODIGO_RASTREIO)));

                int flagVaiBuscar = cursor.getInt(cursor.getColumnIndex(VendasProvider.FLAG_VAI_BUSCAR));
                v.setFlagVaiBuscar(flagVaiBuscar == 1);

                int flagJaBuscou = cursor.getInt(cursor.getColumnIndex(VendasProvider.FLAG_JA_BUSCOU));
                v.setFlagJaBuscou(flagJaBuscou == 1);


                try {

                    String str = cursor.getString(cursor.getColumnIndex(VendasProvider.ANEXOS_JSON_ARRAY));
                    JSONArray anexosArray = new JSONArray(str);
                    if (anexosArray.length() > 0) {
                        String[] anexos = new String[anexosArray.length()];
                        for (int i = 0; i < anexosArray.length(); ++i)
                            anexos[i] = anexosArray.getString(i);

                        v.setAnexos(anexos);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                try {

                    JSONObject clienteJSONObj = new JSONObject(cursor.getString(cursor.getColumnIndex(VendasProvider.CLIENTE)));
//                    Log.d(getClass().getName(),clienteJSONObj.toString());

                    Cliente cliente = new Cliente();
                    cliente.setId(clienteJSONObj.getLong("id"));
                    cliente.setNome(clienteJSONObj.getString("nome"));
                    cliente.setCelular(clienteJSONObj.getString("celular"));
                    cliente.setDddCelular(clienteJSONObj.getString("dddCelular"));
                    cliente.setDddTelefone(clienteJSONObj.getString("dddTelefone"));
                    cliente.setTelefone(clienteJSONObj.getString("telefone"));

                    JSONObject enderecoJSONObj = clienteJSONObj.getJSONObject("endereco");
                    cliente.setEndereco(enderecoJSONObj.getString("complemento"));
                    cliente.setBairro(enderecoJSONObj.getString("bairro"));
                    cliente.setCep(enderecoJSONObj.getString("cep"));

                    long idCity = enderecoJSONObj.getJSONObject("cidade").getLong("id");
                    Cidade cidade = Cidade.fromId(idCity, ctx);
                    cliente.setCidade(cidade);

                    v.setCliente(cliente);

                    JSONArray itens = new JSONArray(cursor.getString(cursor.getColumnIndex(VendasProvider.CARRINHO)));

                    for (int i = 0; i < itens.length(); ++i) {
                        JSONObject jsonObj = itens.getJSONObject(i);

                        long id = jsonObj.getLong("id");
                        String produto = jsonObj.getString("produto_nome");
                        Long produtoID = jsonObj.getLong("produto_id");
                        String unidade = jsonObj.getString("unidade");
                        Integer quantidade = jsonObj.getInt("quantidade");
                        BigDecimal precoAVistaEmReais = BigDecimal.valueOf(jsonObj.getDouble("precoAVistaEmCentavos") / 100d);
                        BigDecimal precoAPrazoEmReais = BigDecimal.valueOf(jsonObj.getDouble("precoAPrazoEmCentavos") / 100d);

                        ItemVenda item = new ItemVenda(id, produtoID, produto, unidade, quantidade, precoAVistaEmReais, precoAPrazoEmReais);
                        v.addToItens(item);
                    }
                    if (v.getItens() == null) {
                        int i = 0;
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                vendas.add(v);

            }
            while (cursor.moveToNext());
        }

        return vendas;
    }


    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return this.vendasPorDataDeEntrega.get(
                datasDeEntregas.get(groupPosition)).get(childPosition);

    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(final int groupPosition, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {

        final Venda venda = (Venda) getChild(groupPosition, childPosition);

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_row_venda_detail, null);
        }

        if (venda.getVendedor() == null)
            convertView.setBackgroundColor(Color.WHITE);
        else
            switch (venda.getVendedor()) {
                case Lucas:
                    convertView.setBackgroundColor(BLUE_LUCAS);
                    break;
                case Adna:
                    convertView.setBackgroundColor(PINK_ADNA);
                    break;
                case MariaClara:
                    convertView.setBackgroundColor(AMARELO_MCLARA);
                    break;
                default:

            }

        convertView.setTag(venda.getId());

        TextView txtCliente = (TextView) convertView.findViewById(R.id.txtCliente);
        txtCliente.setText(venda.getCliente().getNome());

        TextView txtValor = (TextView) convertView.findViewById(R.id.txtValor);
        txtValor.setText(String.format("R$ %.2f", venda.getValorTotal()));

        TextView txtTurno = (TextView) convertView.findViewById(R.id.txtTurno);
        ImageView imgCustomerPickUp = (ImageView) convertView.findViewById(R.id.image_customer_pick_up);

        if (!venda.isFlagVaiBuscar()) {
            txtTurno.setVisibility(View.VISIBLE);
            txtTurno.setText(venda.getTurnoEntrega().name());

            imgCustomerPickUp.setVisibility(View.INVISIBLE);
        } else {
            txtTurno.setVisibility(View.INVISIBLE);

            imgCustomerPickUp.setVisibility(View.VISIBLE);

            Drawable originalIcon = ctx.getResources().getDrawable(R.drawable.customer_pick_up);
            if (!venda.isFlagJaBuscou()) {
                Drawable dimmedIcon = Utilities.convertDrawableToGrayScale(originalIcon);
                imgCustomerPickUp.setBackground(dimmedIcon);
            } else
                imgCustomerPickUp.setBackground(originalIcon);
        }

        ImageView img = (ImageView) convertView.findViewById(R.id.imgFormaPagamento);
        img.setVisibility(View.INVISIBLE);

        StatusVenda statusVenda = venda.getStatus();
        FormaPagamento formaDePagamento = venda.getFormaDePagamento();

        if (statusVenda.equals(StatusVenda.PagamentoRecebido)) {

            switch (formaDePagamento) {
                case AVista:
                    img.setBackgroundResource(R.drawable.dollar_currency_sign);
                    img.setVisibility(View.VISIBLE);
                    break;
                case PagSeguro:
                    img.setBackgroundResource(R.drawable.credit_card_icon);
                    img.setVisibility(View.VISIBLE);
                    break;

            }
        }

        if (statusVenda.equals(StatusVenda.AguardandoPagamento) && formaDePagamento.equals(FormaPagamento.PagSeguro)) {
            Drawable originalIcon = ctx.getResources().getDrawable(R.drawable.credit_card_icon);
            Drawable dimmedIcon = Utilities.convertDrawableToGrayScale(originalIcon);
            img.setBackground(dimmedIcon);
            img.setVisibility(View.VISIBLE);
        }

        boolean hasAttatchment = venda.getAnexos() != null;
        boolean wasDispached = !TextUtils.isEmpty(venda.getCodigoRastreio());
        ImageView flag1 = (ImageView) convertView.findViewById(R.id.flag1);
        flag1.setImageResource(android.R.color.transparent);

        ImageView flag2 = (ImageView) convertView.findViewById(R.id.flag2);
        flag2.setImageResource(android.R.color.transparent);

        if (hasAttatchment && wasDispached){
            flag1.setImageResource(R.drawable.paperclip3_black);
            flag2.setImageResource(R.drawable.icon_correios);
        }else if (!hasAttatchment && wasDispached){
            flag1.setImageResource(R.drawable.icon_correios);
        }else if (hasAttatchment && !wasDispached){
            flag1.setImageResource(R.drawable.paperclip3_black);
        }

        if (venda.getItens() != null) {
            TextView txtView = (TextView) convertView.findViewById(R.id.txtItensVenda);
            txtView.setText(Html.fromHtml(TextUtils.join("<br>", venda.getItens())));
        }

        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (vendasPorDataDeEntrega != null && datasDeEntregas != null) {
            Long key = datasDeEntregas.get(groupPosition);
            return this.vendasPorDataDeEntrega.get(key).size();
        }
        else
            return 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        Long time = datasDeEntregas.get(groupPosition);

        if (time == CORREIOS_KEY)
            return "CORREIOS";
        else {
            Calendar c = new GregorianCalendar(TimeZone.getTimeZone("Etc/UTC"));
            c.setTimeInMillis(time);
            return sdf.format(c.getTime());
        }
    }

    @Override
    public int getGroupCount() {
        if (this.datasDeEntregas != null)
            return this.datasDeEntregas.size();
        else
            return 0;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_row_group_venda, null);
        }
        String text = (String) getGroup(groupPosition);
        ((CheckedTextView) convertView).setText(text);
        ((CheckedTextView) convertView).setChecked(isExpanded);
        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }


    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

}
